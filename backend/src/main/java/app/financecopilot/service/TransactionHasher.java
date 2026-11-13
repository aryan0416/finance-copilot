package app.financecopilot.service;

import app.financecopilot.parsing.ParsedRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Content hash used to skip rows already imported (re-uploads, overlapping statement periods).
 * Identical rows inside one file (two ₹20 chai payments on the same day) get an occurrence suffix so they
 * stay distinct, while re-importing the same file reproduces the same hashes.
 */
final class TransactionHasher {

    private final Map<String, Integer> occurrences = new HashMap<>();

    String hash(ParsedRow row) {
        String base = String.join("|",
                row.date().toString(),
                scale(row.amount()),
                row.direction().name(),
                row.description().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(),
                row.balance() == null ? "" : scale(row.balance()));
        int occurrence = occurrences.merge(base, 1, Integer::sum);
        return sha256(base + "#" + occurrence);
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
