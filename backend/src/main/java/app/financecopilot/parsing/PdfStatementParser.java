package app.financecopilot.parsing;

import app.financecopilot.domain.Direction;
import app.financecopilot.domain.FileType;
import app.financecopilot.parsing.ParseResult.RowFailure;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-based PDF statements (no OCR). PDFBox extracts text in reading order; each line that starts with a
 * date and ends with money columns is a transaction. Supported tails:
 * <ul>
 *   <li>{@code amount Dr|Cr balance}: direction from the suffix</li>
 *   <li>{@code amount balance}: direction inferred from the running balance</li>
 *   <li>{@code debit credit balance}: whichever of debit/credit is non-zero</li>
 * </ul>
 * Lines without a leading date that follow a transaction are treated as wrapped description text.
 */
@Component
public class PdfStatementParser implements StatementParser {

    private static final String DATE = "(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{1,2}[ -][A-Za-z]{3}[ ,-]*\\d{2,4})";
    private static final String MONEY = "-?[\\d,]+\\.\\d{2}";
    private static final Pattern LINE_START = Pattern.compile("^\\s*" + DATE + "\\s+(.*)$");
    private static final Pattern TRAILING_MONEY = Pattern.compile(
            "\\s+(" + MONEY + ")(?:\\s*(Dr|Cr|DR|CR|dr|cr)\\b\\.?)?\\s*$");
    private static final Pattern NOISE_LINE = Pattern.compile(
            "(?i).*(opening balance|closing balance|page \\d+|statement of account|computer generated|end of statement"
                    + "|date\\s+(narration|description|particulars)).*");

    @Override
    public FileType fileType() {
        return FileType.PDF;
    }

    @Override
    public ParseResult parse(byte[] content) throws IOException {
        String text;
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        }
        if (text.isBlank()) {
            throw new StatementFormatException("The PDF has no text layer (scanned statements are not supported)");
        }
        return parseText(text);
    }

    ParseResult parseText(String text) {
        List<ParsedRow> rows = new ArrayList<>();
        List<RowFailure> failures = new ArrayList<>();
        String[] lines = text.split("\\R");
        BigDecimal previousBalance = null;
        PendingRow pending = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            int lineNumber = i + 1;
            if (line.isEmpty()) {
                continue;
            }
            Matcher start = LINE_START.matcher(line);
            Optional<LocalDate> date = start.matches() ? ParsingSupport.parseDate(start.group(1)) : Optional.empty();
            if (date.isEmpty()) {
                if (pending == null) {
                    continue;
                }
                if (NOISE_LINE.matcher(line).matches() || TRAILING_MONEY.matcher(line).find()) {
                    // Headers, footers and totals end the current row: nothing after them is its narration.
                    rows.add(pending.toRow());
                    pending = null;
                } else {
                    pending = pending.appendDescription(line);
                }
                continue;
            }
            if (pending != null) {
                rows.add(pending.toRow());
            }
            pending = null;

            Optional<Tail> tail = Tail.parse(start.group(2));
            if (tail.isEmpty()) {
                failures.add(new RowFailure(lineNumber, "No amount found on dated line"));
                continue;
            }
            Tail t = tail.get();
            Optional<Direction> direction = t.direction(previousBalance);
            if (direction.isEmpty()) {
                failures.add(new RowFailure(lineNumber, "Could not tell debit from credit"));
                previousBalance = t.balance;
                continue;
            }
            pending = new PendingRow(lineNumber, date.get(), t.description, t.amount.abs(), direction.get(), t.balance);
            if (t.balance != null) {
                previousBalance = t.balance;
            }
        }
        if (pending != null) {
            rows.add(pending.toRow());
        }
        return new ParseResult(rows, failures);
    }

    private record PendingRow(int lineNumber, LocalDate date, String description, BigDecimal amount,
                              Direction direction, BigDecimal balance) {

        PendingRow appendDescription(String more) {
            return new PendingRow(lineNumber, date, description + " " + more, amount, direction, balance);
        }

        ParsedRow toRow() {
            return new ParsedRow(lineNumber, date, description.replaceAll("\\s+", " ").trim(), amount, direction, balance);
        }
    }

    /** The money columns at the end of a dated line, plus the description before them. */
    private record Tail(String description, BigDecimal amount, String suffix, BigDecimal debit, BigDecimal credit,
                        BigDecimal balance) {

        static Optional<Tail> parse(String rest) {
            List<String> values = new ArrayList<>();
            List<String> suffixes = new ArrayList<>();
            String remaining = rest;
            Matcher m;
            while (values.size() < 3 && (m = TRAILING_MONEY.matcher(remaining)).find()) {
                values.addFirst(m.group(1));
                suffixes.addFirst(m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT));
                remaining = remaining.substring(0, m.start());
            }
            String description = remaining.trim();
            if (values.isEmpty() || description.isEmpty()) {
                return Optional.empty();
            }
            BigDecimal[] nums = values.stream().map(Tail::money).toArray(BigDecimal[]::new);
            return switch (nums.length) {
                case 1 -> Optional.of(new Tail(description, nums[0], suffixes.get(0), null, null, null));
                case 2 -> Optional.of(new Tail(description, nums[0], suffixes.get(0), null, null, nums[1]));
                default -> Optional.of(new Tail(description, nums[0].signum() != 0 ? nums[0] : nums[1],
                        "", nums[0], nums[1], nums[2]));
            };
        }

        Optional<Direction> direction(BigDecimal previousBalance) {
            if (debit != null) {
                if (debit.signum() != 0 && credit.signum() == 0) {
                    return Optional.of(Direction.DEBIT);
                }
                if (credit.signum() != 0 && debit.signum() == 0) {
                    return Optional.of(Direction.CREDIT);
                }
                return Optional.empty();
            }
            if (suffix.equals("dr")) {
                return Optional.of(Direction.DEBIT);
            }
            if (suffix.equals("cr")) {
                return Optional.of(Direction.CREDIT);
            }
            if (amount.signum() < 0) {
                return Optional.of(Direction.DEBIT);
            }
            if (balance != null && previousBalance != null) {
                int delta = balance.compareTo(previousBalance);
                return delta == 0 ? Optional.empty() : Optional.of(delta < 0 ? Direction.DEBIT : Direction.CREDIT);
            }
            return Optional.empty();
        }

        private static BigDecimal money(String raw) {
            return new BigDecimal(raw.replace(",", ""));
        }
    }
}
