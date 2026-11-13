package app.financecopilot.service;

import app.financecopilot.categorization.CategorizationInput;
import app.financecopilot.categorization.CategorizationResult;
import app.financecopilot.categorization.CategorizationResult.Assignment;
import app.financecopilot.categorization.CategorizationService;
import app.financecopilot.domain.FileType;
import app.financecopilot.domain.Statement;
import app.financecopilot.dto.StatementDtos.CategorizationSummary;
import app.financecopilot.dto.StatementDtos.FailedRow;
import app.financecopilot.dto.StatementDtos.ImportSummary;
import app.financecopilot.dto.StatementDtos.StatementResponse;
import app.financecopilot.exception.ApiException;
import app.financecopilot.parsing.MerchantNormalizer;
import app.financecopilot.parsing.ParseResult;
import app.financecopilot.parsing.ParsedRow;
import app.financecopilot.parsing.StatementFormatException;
import app.financecopilot.parsing.StatementParser;
import app.financecopilot.repository.StatementRepository;
import app.financecopilot.repository.TransactionBatchWriter;
import app.financecopilot.repository.TransactionBatchWriter.NewTransaction;
import app.financecopilot.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Upload pipeline: detect format → parse → normalise merchants → drop duplicates → categorise → persist.
 * Categorisation (which may call the LLM) runs outside the DB transaction so a slow model never holds a
 * pooled connection; only the final write is transactional.
 */
@Service
public class StatementImportService {

    private static final Logger log = LoggerFactory.getLogger(StatementImportService.class);
    private static final int MAX_REPORTED_FAILURES = 25;

    private final Map<FileType, StatementParser> parsers = new EnumMap<>(FileType.class);
    private final MerchantNormalizer merchantNormalizer;
    private final CategorizationService categorizationService;
    private final StatementRepository statements;
    private final TransactionRepository transactions;
    private final TransactionBatchWriter batchWriter;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;

    public StatementImportService(List<StatementParser> parsers, MerchantNormalizer merchantNormalizer,
                                  CategorizationService categorizationService, StatementRepository statements,
                                  TransactionRepository transactions, TransactionBatchWriter batchWriter,
                                  TransactionTemplate transactionTemplate, ApplicationEventPublisher events) {
        parsers.forEach(p -> this.parsers.put(p.fileType(), p));
        this.merchantNormalizer = merchantNormalizer;
        this.categorizationService = categorizationService;
        this.statements = statements;
        this.transactions = transactions;
        this.batchWriter = batchWriter;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
    }

    /** Published after a successful import so derived data (subscriptions) can be refreshed. */
    public record StatementImported(Long userId, Long statementId) {
    }

    public ImportSummary importStatement(Long userId, String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
        String safeName = fileName == null || fileName.isBlank() ? "statement" : fileName.strip();
        FileType type = detectType(safeName, content);
        String fileHash = TransactionHasher.sha256(content);
        if (statements.existsByUserIdAndFileHash(userId, fileHash)) {
            throw ApiException.conflict("This statement has already been uploaded");
        }

        ParseResult parsed = parse(type, content);
        if (parsed.rows().isEmpty()) {
            throw ApiException.badRequest("No transactions found in the file");
        }

        // Hash every row, then drop those the user already has (overlapping statements, re-exports).
        TransactionHasher hasher = new TransactionHasher();
        List<String> hashes = parsed.rows().stream().map(hasher::hash).toList();
        Set<String> existing = new HashSet<>(transactions.findExistingHashes(userId, hashes));
        List<ParsedRow> fresh = new ArrayList<>();
        List<String> freshHashes = new ArrayList<>();
        for (int i = 0; i < parsed.rows().size(); i++) {
            if (!existing.contains(hashes.get(i))) {
                fresh.add(parsed.rows().get(i));
                freshHashes.add(hashes.get(i));
            }
        }

        List<String> merchants = fresh.stream().map(r -> merchantNormalizer.normalize(r.description())).toList();
        List<CategorizationInput> inputs = new ArrayList<>(fresh.size());
        for (int i = 0; i < fresh.size(); i++) {
            ParsedRow row = fresh.get(i);
            inputs.add(new CategorizationInput(i, merchants.get(i), row.description(), row.direction()));
        }
        CategorizationResult categorization = categorizationService.categorize(userId, inputs);

        LocalDate start = parsed.rows().stream().map(ParsedRow::date).min(Comparator.naturalOrder()).orElse(null);
        LocalDate end = parsed.rows().stream().map(ParsedRow::date).max(Comparator.naturalOrder()).orElse(null);

        Persisted persisted = transactionTemplate.execute(status -> {
            Statement statement = statements.save(new Statement(userId, safeName, type, fileHash));
            List<NewTransaction> rows = new ArrayList<>(fresh.size());
            for (int i = 0; i < fresh.size(); i++) {
                ParsedRow row = fresh.get(i);
                Assignment assignment = categorization.assignments().get(i);
                rows.add(new NewTransaction(userId, statement.getId(), row.date(), row.description(), merchants.get(i),
                        row.amount(), row.direction(), row.balance(), assignment.categoryId(), assignment.source(),
                        freshHashes.get(i)));
            }
            int inserted = batchWriter.insertAll(rows);
            int duplicates = parsed.rows().size() - inserted;
            statement.recordResult(inserted, duplicates, parsed.failures().size(), start, end);
            return new Persisted(statement.getId(), inserted, duplicates);
        });

        log.info("Imported statement {} for user {}: {} new, {} duplicate, {} failed",
                persisted.statementId(), userId, persisted.inserted(), persisted.duplicates(), parsed.failures().size());
        events.publishEvent(new StatementImported(userId, persisted.statementId()));

        var stats = categorization.stats();
        List<FailedRow> failures = parsed.failures().stream().limit(MAX_REPORTED_FAILURES)
                .map(f -> new FailedRow(f.lineNumber(), f.reason())).toList();
        return new ImportSummary(persisted.statementId(), safeName, type, persisted.inserted(), persisted.duplicates(),
                parsed.failures().size(), failures, start, end,
                new CategorizationSummary(stats.byRule(), stats.byCache(), stats.byAi(), stats.uncategorised(),
                        stats.withoutAiPercent()));
    }

    public List<StatementResponse> list(Long userId) {
        return statements.findByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(s -> new StatementResponse(s.getId(), s.getFileName(), s.getFileType(), s.getRowsImported(),
                        s.getRowsDuplicate(), s.getRowsFailed(), s.getPeriodStart(), s.getPeriodEnd(), s.getUploadedAt()))
                .toList();
    }

    private record Persisted(Long statementId, int inserted, int duplicates) {
    }

    private ParseResult parse(FileType type, byte[] content) {
        try {
            return parsers.get(type).parse(content);
        } catch (StatementFormatException e) {
            throw ApiException.badRequest(e.getMessage());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the " + type + " file");
        }
    }

    static FileType detectType(String fileName, byte[] content) {
        boolean pdfMagic = content.length >= 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                && content[3] == 'F';
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (pdfMagic) {
            return FileType.PDF;
        }
        if (lower.endsWith(".pdf")) {
            throw ApiException.badRequest("The file has a .pdf extension but is not a valid PDF");
        }
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            return FileType.CSV;
        }
        throw ApiException.badRequest("Unsupported file type: upload a CSV or a text-based PDF statement");
    }
}
