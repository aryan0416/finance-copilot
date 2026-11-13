package app.financecopilot.parsing;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns raw bank narrations into a stable merchant name, e.g.
 * <pre>
 *   UPI/628712345678/SWIGGY/swiggy@icici/Payment      -> Swiggy
 *   UPI-ZOMATO LTD-ZOMATO@HDFCBANK-HDFC0000001-...     -> Zomato
 *   POS 4321XXXXXXXX1234 DMART AVENUE SUPERM           -> Dmart Avenue Superm
 *   NEFT/CR/HDFC0001234/ACME TECHNOLOGIES PVT LTD      -> Acme Technologies
 * </pre>
 * A stable name matters twice: categorisation rules and the merchant cache key on it, and subscription
 * detection groups by it.
 */
@Component
public class MerchantNormalizer {

    static final int MAX_LENGTH = 120;

    /** Well-known brands collapse to one canonical spelling regardless of the payment rail. */
    private static final Map<String, String> BRANDS = new LinkedHashMap<>();

    static {
        String[][] brands = {
                {"swiggy", "Swiggy"}, {"zomato", "Zomato"}, {"amazon", "Amazon"}, {"amzn", "Amazon"},
                {"flipkart", "Flipkart"}, {"myntra", "Myntra"}, {"uber", "Uber"}, {"olacabs", "Ola"},
                {"ola cabs", "Ola"}, {"ani technologies", "Ola"}, {"rapido", "Rapido"}, {"netflix", "Netflix"},
                {"spotify", "Spotify"}, {"hotstar", "Disney+ Hotstar"}, {"bigbasket", "BigBasket"},
                {"blinkit", "Blinkit"}, {"grofers", "Blinkit"}, {"zepto", "Zepto"}, {"irctc", "IRCTC"},
                {"airtel", "Airtel"}, {"jiomart", "JioMart"}, {"reliance jio", "Jio"}, {"jio", "Jio"}, {"bookmyshow", "BookMyShow"},
                {"makemytrip", "MakeMyTrip"}, {"indigo", "IndiGo"}, {"interglobe", "IndiGo"},
                {"youtube", "YouTube"}, {"google play", "Google Play"}, {"apple.com", "Apple"},
                {"cult.fit", "Cult.fit"}, {"cultfit", "Cult.fit"}, {"curefit", "Cult.fit"},
                {"groww", "Groww"}, {"zerodha", "Zerodha"}, {"dmart", "DMart"}, {"croma", "Croma"},
                {"nykaa", "Nykaa"}, {"ajio", "Ajio"}, {"bescom", "BESCOM"}, {"pharmeasy", "PharmEasy"},
                {"apollo", "Apollo Pharmacy"}, {"lenskart", "Lenskart"}, {"decathlon", "Decathlon"},
        };
        for (String[] b : brands) {
            BRANDS.put(b[0], b[1]);
        }
    }

    private static final Set<String> RAIL_TOKENS = Set.of(
            "upi", "imps", "neft", "rtgs", "pos", "ach", "nach", "ecs", "mmt", "inb", "ib", "mb", "bil", "billpay",
            "onl", "ecom", "p2m", "p2a", "dr", "cr", "txn", "ref", "payment", "pay", "paid", "to", "from", "by",
            "transfer", "trf", "sent", "received", "using", "via", "collect", "request", "mandate", "autopay",
            "si", "debit", "credit", "card", "purchase", "vps", "ipay", "na", "yesb", "hdfc", "icic", "sbin",
            "utib", "kkbk", "pytm", "ybl", "okaxis", "oksbi", "okhdfcbank", "okicici", "paytm", "apl", "ibl");

    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "pvt", "private", "ltd", "limited", "llp", "inc", "india", "co", "corp", "corporation", "com", "in");

    private static final Pattern SEPARATORS = Pattern.compile("[/|\\\\:*#]+|\\s-\\s|(?<=\\S)-(?=\\S)");
    private static final Pattern IFSC = Pattern.compile("(?i)^[a-z]{4}0[a-z0-9]{6}$");
    private static final Pattern MASKED_CARD = Pattern.compile("(?i)^[0-9x*]{8,}$");
    private static final Pattern HAS_DIGIT_RUN = Pattern.compile("\\d{5,}");

    public String normalize(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "Unknown";
        }
        String lower = rawDescription.toLowerCase(Locale.ROOT);
        if (lower.contains("atm") && (lower.contains("wdl") || lower.contains("withdraw") || lower.contains("cash"))) {
            return "ATM Withdrawal";
        }
        if (lower.startsWith("interest") || lower.startsWith("int.pd") || lower.contains("interest paid")) {
            return "Bank Interest";
        }
        if (lower.contains("payment received") || lower.contains("payment - thank you")) {
            return "Card Payment";
        }
        for (Map.Entry<String, String> brand : BRANDS.entrySet()) {
            if (containsWord(lower, brand.getKey())) {
                return brand.getValue();
            }
        }

        String vpaHandle = null;
        for (String segment : SEPARATORS.split(rawDescription)) {
            String candidate = segment.trim();
            if (candidate.contains("@")) {
                if (vpaHandle == null) {
                    vpaHandle = candidate.substring(0, candidate.indexOf('@')).replaceAll("[^A-Za-z ]", " ");
                }
                continue;
            }
            String cleaned = clean(candidate);
            if (!cleaned.isEmpty()) {
                return truncate(cleaned);
            }
        }
        if (vpaHandle != null && !clean(vpaHandle).isEmpty()) {
            return truncate(clean(vpaHandle));
        }
        return truncate(titleCase(rawDescription.replaceAll("[^A-Za-z0-9 ]", " ").trim()));
    }

    /** Drops rail keywords, reference numbers, IFSCs, card masks and legal suffixes; title-cases the rest. */
    private static String clean(String segment) {
        if (IFSC.matcher(segment.replace(" ", "")).matches() || MASKED_CARD.matcher(segment.replace(" ", "")).matches()) {
            return "";
        }
        String[] words = segment.replaceAll("[^A-Za-z0-9&.' ]", " ").trim().split("\\s+");
        String kept = Arrays.stream(words)
                .filter(w -> !w.isBlank())
                .filter(w -> !RAIL_TOKENS.contains(w.toLowerCase(Locale.ROOT)))
                .filter(w -> !HAS_DIGIT_RUN.matcher(w).find())
                .filter(w -> !MASKED_CARD.matcher(w).matches())
                .collect(Collectors.joining(" "));
        // Strip trailing legal suffixes ("ACME TECHNOLOGIES PVT LTD" -> "Acme Technologies"), keeping one word.
        String[] parts = kept.split("\\s+");
        int end = parts.length;
        while (end > 1 && LEGAL_SUFFIXES.contains(parts[end - 1].toLowerCase(Locale.ROOT).replace(".", ""))) {
            end--;
        }
        String result = String.join(" ", Arrays.copyOf(parts, end)).trim();
        // Needs at least three letters to be a merchant name rather than leftover noise.
        return result.chars().filter(Character::isLetter).count() >= 3 ? titleCase(result) : "";
    }

    private static boolean containsWord(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        while (idx >= 0) {
            boolean startOk = idx == 0 || !Character.isLetter(haystack.charAt(idx - 1));
            int after = idx + needle.length();
            // Allow trailing digits/letters for handles like "swiggy8@ybl" or "netflixupi", but require a
            // word start so "jio" doesn't match "rajiov".
            if (startOk || needle.length() >= 6) {
                return true;
            }
            idx = haystack.indexOf(needle, after);
        }
        return false;
    }

    private static String titleCase(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH).trim();
    }
}
