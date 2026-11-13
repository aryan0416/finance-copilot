package app.financecopilot.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Date and amount parsing tolerant of the formats Indian banks export. */
final class ParsingSupport {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            pattern("dd/MM/uuuu"), pattern("d/M/uuuu"), pattern("dd-MM-uuuu"), pattern("d-M-uuuu"),
            pattern("dd.MM.uuuu"), pattern("uuuu-MM-dd"),
            pattern("dd-MMM-uuuu"), pattern("d-MMM-uuuu"), pattern("dd MMM uuuu"), pattern("d MMM uuuu"),
            pattern("dd/MMM/uuuu"), pattern("dd MMM, uuuu"),
            twoDigitYear("dd/MM/"), twoDigitYear("dd-MM-"), twoDigitYear("dd-MMM-"), twoDigitYear("dd MMM "));

    private static final Pattern AMOUNT_NOISE = Pattern.compile("(?i)(₹|inr|rs\\.?|\\s|,)");
    private static final Pattern DR_CR_SUFFIX = Pattern.compile("(?i)\\s*(dr|cr)\\.?$");

    private ParsingSupport() {
    }

    static Optional<LocalDate> parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        // Some exports append a time ("01/10/2026 10:15:22"); keep the date part.
        int space = value.indexOf(' ');
        if (space > 0 && value.substring(space).trim().matches("\\d{1,2}:\\d{2}.*")) {
            value = value.substring(0, space);
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(value, format));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return Optional.empty();
    }

    /**
     * Parses "1,23,456.78", "₹ 450", "(120.00)", "-99.5", "1200.00 Cr". Returns a signed value: a leading
     * minus, parentheses or a "Dr" suffix make it negative. Empty / "-" / "0" cells return empty.
     */
    static Optional<BigDecimal> parseAmount(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.isEmpty() || value.equals("-")) {
            return Optional.empty();
        }
        boolean negative = false;
        var suffix = DR_CR_SUFFIX.matcher(value);
        if (suffix.find()) {
            negative = suffix.group(1).equalsIgnoreCase("dr");
            value = value.substring(0, suffix.start());
        }
        value = AMOUNT_NOISE.matcher(value).replaceAll("");
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("-")) {
            negative = !negative;
            value = value.substring(1);
        } else if (value.startsWith("+")) {
            value = value.substring(1);
        }
        if (!value.matches("\\d+(\\.\\d+)?")) {
            return Optional.empty();
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(negative ? amount.negate() : amount);
    }

    private static DateTimeFormatter pattern(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH);
    }

    /** "dd/MM/yy" style, interpreting the year as 2000-2099. */
    private static DateTimeFormatter twoDigitYear(String prefix) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(prefix)
                .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                .toFormatter(Locale.ENGLISH);
    }
}
