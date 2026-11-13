package app.financecopilot.parsing;

import app.financecopilot.domain.Direction;
import app.financecopilot.domain.FileType;
import app.financecopilot.parsing.ParseResult.RowFailure;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Generic CSV statement parser. Bank exports differ in column names, order, delimiters and preamble
 * lines, so the header row is located by scanning for recognisable column names, and each column role
 * (date / description / debit / credit / amount / type / balance) is detected from synonyms.
 */
@Component
public class CsvStatementParser implements StatementParser {

    private static final int MAX_HEADER_SCAN_ROWS = 30;

    @Override
    public FileType fileType() {
        return FileType.CSV;
    }

    @Override
    public ParseResult parse(byte[] content) throws IOException {
        String text = new String(content, StandardCharsets.UTF_8).replace("﻿", "");
        List<String[]> lines = readAll(text, detectSeparator(text));

        int headerIndex = -1;
        ColumnMapping mapping = null;
        for (int i = 0; i < Math.min(lines.size(), MAX_HEADER_SCAN_ROWS); i++) {
            Optional<ColumnMapping> candidate = ColumnMapping.detect(lines.get(i));
            if (candidate.isPresent()) {
                headerIndex = i;
                mapping = candidate.get();
                break;
            }
        }
        if (mapping == null) {
            throw new StatementFormatException(
                    "Could not find a header row with date, description and amount columns");
        }

        List<ParsedRow> rows = new ArrayList<>();
        List<RowFailure> failures = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            String[] cells = lines.get(i);
            int lineNumber = i + 1;
            if (isBlank(cells)) {
                continue;
            }
            Optional<LocalDate> date = ParsingSupport.parseDate(mapping.cell(cells, mapping.date));
            if (date.isEmpty()) {
                // Footer lines ("Opening balance", "*** End of statement ***") have no date: skip quietly
                // unless the line also carries an amount, in which case it is a broken transaction row.
                if (mapping.hasAnyAmount(cells)) {
                    failures.add(new RowFailure(lineNumber, "Unrecognised date: " + mapping.cell(cells, mapping.date)));
                }
                continue;
            }
            String description = mapping.cell(cells, mapping.description).trim().replaceAll("\\s+", " ");
            if (description.isEmpty()) {
                failures.add(new RowFailure(lineNumber, "Missing description"));
                continue;
            }
            Optional<SignedAmount> amount = mapping.amount(cells);
            if (amount.isEmpty()) {
                failures.add(new RowFailure(lineNumber, "Missing or invalid amount"));
                continue;
            }
            BigDecimal balance = ParsingSupport.parseAmount(mapping.cell(cells, mapping.balance)).orElse(null);
            rows.add(new ParsedRow(lineNumber, date.get(), description, amount.get().value(),
                    amount.get().direction(), balance));
        }
        return new ParseResult(rows, failures);
    }

    private static List<String[]> readAll(String text, char separator) throws IOException {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(text))
                .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
                .build()) {
            return reader.readAll();
        } catch (CsvException e) {
            throw new StatementFormatException("Malformed CSV near line " + e.getLineNumber());
        }
    }

    /** Picks the most frequent of , ; \t | in the first lines. */
    static char detectSeparator(String text) {
        String sample = text.lines().limit(20).reduce("", (a, b) -> a + "\n" + b);
        char best = ',';
        long bestCount = -1;
        for (char c : new char[]{',', ';', '\t', '|'}) {
            long count = sample.chars().filter(ch -> ch == c).count();
            if (count > bestCount) {
                best = c;
                bestCount = count;
            }
        }
        return best;
    }

    private static boolean isBlank(String[] cells) {
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    record SignedAmount(BigDecimal value, Direction direction) {
    }

    /** Column indexes for each role; -1 when the column is absent. */
    static final class ColumnMapping {

        // Ordered by preference: "txn date" beats "value date" when both exist.
        private static final List<String> DATE = List.of("transaction date", "txn date", "tran date", "trans date",
                "posting date", "date", "value date", "value dt");
        private static final List<String> DESCRIPTION = List.of("narration", "description", "particulars",
                "transaction remarks", "remarks", "transaction details", "details");
        private static final List<String> DEBIT = List.of("withdrawal", "debit", "dr amount", "paid out", "dr");
        private static final List<String> CREDIT = List.of("deposit", "credit", "cr amount", "paid in", "cr");
        private static final List<String> AMOUNT = List.of("transaction amount", "amount", "amt");
        private static final List<String> TYPE = List.of("dr/cr", "cr/dr", "dr / cr", "debit/credit",
                "transaction type", "type");
        private static final List<String> BALANCE = List.of("closing balance", "available balance", "balance");

        final int date;
        final int description;
        final int debit;
        final int credit;
        final int amount;
        final int type;
        final int balance;

        private ColumnMapping(int date, int description, int debit, int credit, int amount, int type, int balance) {
            this.date = date;
            this.description = description;
            this.debit = debit;
            this.credit = credit;
            this.amount = amount;
            this.type = type;
            this.balance = balance;
        }

        static Optional<ColumnMapping> detect(String[] header) {
            String[] names = new String[header.length];
            for (int i = 0; i < header.length; i++) {
                names[i] = normalise(header[i]);
            }
            boolean[] used = new boolean[names.length];
            // Assign the most specific roles first so "Balance" isn't taken as an amount, etc.
            int balance = claim(names, used, BALANCE);
            int type = claim(names, used, TYPE);
            int date = claim(names, used, DATE);
            int description = claim(names, used, DESCRIPTION);
            int debit = claim(names, used, DEBIT);
            int credit = claim(names, used, CREDIT);
            int amount = claim(names, used, AMOUNT);

            boolean hasAmounts = (debit >= 0 && credit >= 0) || amount >= 0;
            if (date < 0 || description < 0 || !hasAmounts) {
                return Optional.empty();
            }
            return Optional.of(new ColumnMapping(date, description, debit, credit, amount, type, balance));
        }

        /** Finds the first unused column matching a synonym; exact match wins over prefix/contains. */
        private static int claim(String[] names, boolean[] used, List<String> synonyms) {
            for (String synonym : synonyms) {
                for (int i = 0; i < names.length; i++) {
                    if (!used[i] && names[i].equals(synonym)) {
                        used[i] = true;
                        return i;
                    }
                }
            }
            for (String synonym : synonyms) {
                if (synonym.length() <= 3) {
                    continue; // "dr", "cr", "amt" only match exactly
                }
                for (int i = 0; i < names.length; i++) {
                    if (!used[i] && names[i].contains(synonym)) {
                        used[i] = true;
                        return i;
                    }
                }
            }
            return -1;
        }

        private static String normalise(String header) {
            return header == null ? "" : header.toLowerCase(Locale.ROOT)
                    .replaceAll("\\(.*?\\)", " ")       // "Amount (INR)" -> "amount"
                    .replaceAll("[^a-z/ ]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        String cell(String[] cells, int index) {
            return index >= 0 && index < cells.length && cells[index] != null ? cells[index] : "";
        }

        boolean hasAnyAmount(String[] cells) {
            return ParsingSupport.parseAmount(cell(cells, debit)).isPresent()
                    || ParsingSupport.parseAmount(cell(cells, credit)).isPresent()
                    || ParsingSupport.parseAmount(cell(cells, amount)).isPresent();
        }

        Optional<SignedAmount> amount(String[] cells) {
            if (debit >= 0 || credit >= 0) {
                Optional<BigDecimal> dr = ParsingSupport.parseAmount(cell(cells, debit));
                Optional<BigDecimal> cr = ParsingSupport.parseAmount(cell(cells, credit));
                if (dr.isPresent()) {
                    return Optional.of(new SignedAmount(dr.get().abs(), Direction.DEBIT));
                }
                if (cr.isPresent()) {
                    return Optional.of(new SignedAmount(cr.get().abs(), Direction.CREDIT));
                }
                if (amount < 0) {
                    return Optional.empty();
                }
            }
            Optional<BigDecimal> value = ParsingSupport.parseAmount(cell(cells, amount));
            if (value.isEmpty()) {
                return Optional.empty();
            }
            Direction direction = directionFromType(cell(cells, type))
                    .orElse(value.get().signum() < 0 ? Direction.DEBIT : Direction.CREDIT);
            return Optional.of(new SignedAmount(value.get().abs(), direction));
        }

        private static Optional<Direction> directionFromType(String raw) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("dr") || value.startsWith("debit") || value.startsWith("withdraw")) {
                return Optional.of(Direction.DEBIT);
            }
            if (value.startsWith("cr") || value.startsWith("credit") || value.startsWith("deposit")) {
                return Optional.of(Direction.CREDIT);
            }
            return Optional.empty();
        }
    }
}
