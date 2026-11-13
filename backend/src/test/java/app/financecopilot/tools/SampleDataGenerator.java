package app.financecopilot.tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generates realistic (fictional) Indian bank statements into sample-data/:
 * <ul>
 *   <li>{@code hdfc-style-savings-may-oct-2026.csv}: 6 months, HDFC-like columns with a preamble</li>
 *   <li>{@code credit-card-sep-oct-2026.csv}: different layout (single amount column + Dr/Cr)</li>
 *   <li>{@code savings-statement-nov-2026.pdf}: text PDF continuing the savings account</li>
 * </ul>
 * Includes salary, rent, UPI food orders, cabs, streaming/telecom subscriptions, shopping, a few unusually
 * large spends, and local merchants that no built-in rule knows (so the AI categorisation path runs).
 * Deterministic: a fixed seed produces identical files on every run.
 *
 * <p>Run: {@code ./mvnw -q test-compile exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=app.financecopilot.tools.SampleDataGenerator -Dexec.args=../sample-data}
 */
public final class SampleDataGenerator {

    private static final Locale IN = Locale.ENGLISH;
    private final Random random = new Random(20261113L);

    record Txn(LocalDate date, String narration, BigDecimal debit, BigDecimal credit) {
        boolean isDebit() {
            return debit != null;
        }
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "sample-data");
        Files.createDirectories(out);
        SampleDataGenerator generator = new SampleDataGenerator();

        BigDecimal opening = new BigDecimal("85432.10");
        List<Txn> savings = generator.savings(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31));
        BigDecimal closing = writeHdfcCsv(out.resolve("hdfc-style-savings-may-oct-2026.csv"), savings, opening);

        List<Txn> november = generator.savings(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12));
        writePdf(out.resolve("savings-statement-nov-2026.pdf"), november, closing);

        writeCardCsv(out.resolve("credit-card-sep-oct-2026.csv"), generator.creditCard());
        System.out.printf("Wrote %d savings rows (May-Oct), %d PDF rows (Nov) to %s%n",
                savings.size(), november.size(), out.toAbsolutePath());
    }

    // ---------------------------------------------------------------- savings account

    List<Txn> savings(LocalDate from, LocalDate to) {
        List<Txn> txns = new ArrayList<>();
        for (LocalDate month = from.withDayOfMonth(1); !month.isAfter(to); month = month.plusMonths(1)) {
            String mon = month.getMonth().getDisplayName(TextStyle.SHORT, IN).toUpperCase(IN);
            addIfInRange(txns, from, to, month.withDayOfMonth(1), credit(
                    "NEFT CR-CITI0000002-ACME TECHNOLOGIES PVT LTD-SALARY " + mon + " " + month.getYear(), "145000.00"));
            addIfInRange(txns, from, to, month.withDayOfMonth(2), debit(
                    upi("RAMESH KUMAR", "rameshk88@oksbi", "Flat rent " + mon), "28000.00"));
            // Monthly subscriptions: fixed amounts on (almost) fixed days, so the detector finds them.
            addIfInRange(txns, from, to, month.withDayOfMonth(5), debit(
                    "POS 4591XXXXXXXX7712 NETFLIX.COM MUMBAI", "649.00"));
            addIfInRange(txns, from, to, month.withDayOfMonth(8 + random.nextInt(2)), debit(
                    upi("CULTFIT", "cultfit@icici", "Membership"), "1499.00"));
            addIfInRange(txns, from, to, month.withDayOfMonth(10), debit(
                    "ACH D- GROWW MUTUAL FUND SIP-" + digits(9), "10000.00"));
            addIfInRange(txns, from, to, month.withDayOfMonth(12), debit(
                    upi("SPOTIFY INDIA", "spotify@hdfcbank", "AutoPay"), "119.00"));
            addIfInRange(txns, from, to, month.withDayOfMonth(15), debit(
                    "BBPS/AIRTEL XSTREAM FIBER/" + digits(10), "1178.82"));
            addIfInRange(txns, from, to, month.withDayOfMonth(18), debit(
                    "BBPS/BESCOM ELECTRICITY/" + digits(10), money(1100, 2600)));
            addIfInRange(txns, from, to, month.withDayOfMonth(20 + random.nextInt(2)), debit(
                    upi("RELIANCE JIO", "jio@sbi", "Prepaid recharge"), "349.00"));
            addIfInRange(txns, from, to, month.withDayOfMonth(25), debit(
                    "POS 4591XXXXXXXX7712 GOOGLE YOUTUBEPREMIUM", "129.00"));
        }

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            // Weekly milk delivery from a local dairy: unknown merchant + weekly subscription.
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                txns.add(debit(day, upi("DAILY FRESH DAIRY", "dailyfresh.dairy@okaxis", "Weekly milk"), "420.00"));
            }
            maybe(txns, day, 0.42, () -> upi("SWIGGY", "swiggy" + random.nextInt(9) + "@icici", "Order"), 149, 780);
            maybe(txns, day, 0.22, () -> "UPI-ZOMATO LTD-ZOMATO@HDFCBANK-HDFC0000499-" + digits(12) + "-PAYMENT", 180, 950);
            maybe(txns, day, 0.16, () -> upi("BLINKIT", "blinkit.payu@hdfcbank", "Grocery"), 120, 1450);
            maybe(txns, day, 0.10, () -> upi("ZEPTO MARKETPLACE PVT LTD", "zepto@ybl", "Order"), 150, 1100);
            maybe(txns, day, 0.05, () -> upi("BIGBASKET", "bigbasket@icici", "Monthly groceries"), 1200, 3800);
            maybe(txns, day, 0.20, () -> upi("UBER INDIA SYSTEMS PVT LTD", "uber.rides@axisbank", "Trip"), 95, 640);
            maybe(txns, day, 0.12, () -> upi("ANI TECHNOLOGIES PVT LTD", "olacabs@ybl", "Ola ride"), 80, 520);
            maybe(txns, day, 0.10, () -> upi("RAPIDO", "rapido.bike@ybl", "Bike taxi"), 45, 160);
            maybe(txns, day, 0.07, () -> "POS 4591XXXXXXXX7712 AMAZON PAY INDIA PRIVA", 299, 4200);
            maybe(txns, day, 0.04, () -> upi("FLIPKART INTERNET PVT LTD", "flipkart@axisbank", "Order"), 399, 3500);
            maybe(txns, day, 0.03, () -> upi("MYNTRA DESIGNS PVT LTD", "myntra@icici", "Order"), 699, 2999);
            // Local merchants no rule knows: these exercise the AI step and then the shared cache.
            maybe(txns, day, 0.12, () -> upi("SHARMA GENERAL STORE", "sharmastores@paytm", "Paid via app"), 60, 650);
            maybe(txns, day, 0.08, () -> upi("SRI KRISHNA TIFFIN CENTRE", "q" + digits(9) + "@ybl", "Breakfast"), 70, 260);
            maybe(txns, day, 0.05, () -> upi("MEHTA MEDICAL AND GENERAL", "mehtamedical@okhdfcbank", "Medicines"), 90, 1200);
            maybe(txns, day, 0.06, () -> upi("GUPTA FRUIT CENTRE", "guptafruits@okicici", "Fruits"), 80, 420);
            maybe(txns, day, 0.07, () -> "POS 4591XXXXXXXX7712 BLUE TOKAI COFFEE ROAS", 240, 680);
            maybe(txns, day, 0.04, () -> "POS 4591XXXXXXXX7712 THIRD WAVE COFFEE", 220, 560);
            maybe(txns, day, 0.025, () -> upi("LAKSHMI UNISEX SALON", "lakshmisalon@ybl", "Haircut"), 250, 900);
            maybe(txns, day, 0.03, () -> "POS 4591XXXXXXXX7712 SHELL INDIA MARKETS", 800, 2400);
            maybe(txns, day, 0.05, () -> upi("ROHIT VERMA", "rohitv@okaxis", "Dinner split"), 200, 1500);
            maybe(txns, day, 0.03, () -> upi("PRIYA NAIR", "priya.nair@oksbi", "Movie tickets"), 250, 900);
            if (random.nextDouble() < 0.03) {
                txns.add(debit(day, "ATM WDL-HDFC ATM KORAMANGALA-" + digits(6) + "-BANGALORE",
                        String.valueOf((2 + random.nextInt(4)) * 1000) + ".00"));
            }
            if (random.nextDouble() < 0.02) {
                txns.add(credit(day, upi("ROHIT VERMA", "rohitv@okaxis", "Trip share"), money(500, 3000)));
            }
        }

        // Unusually large spends (anomaly detection) and occasional credits.
        addIfInRange(txns, from, to, LocalDate.of(2026, 6, 10), debit(
                "POS 4591XXXXXXXX7712 INTERGLOBE AVIATION LTD", "18450.00"));
        addIfInRange(txns, from, to, LocalDate.of(2026, 7, 7), debit(
                upi("MANIPAL HOSPITALS", "manipalhosp@icici", "OPD and tests"), "22000.00"));
        addIfInRange(txns, from, to, LocalDate.of(2026, 8, 15), debit(
                upi("FLIPKART INTERNET PVT LTD", "flipkart@axisbank", "Big Billion Days"), "64999.00"));
        addIfInRange(txns, from, to, LocalDate.of(2026, 9, 22), debit(
                "POS 4591XXXXXXXX7712 CROMA RETAIL INFINITI", "42990.00"));
        addIfInRange(txns, from, to, LocalDate.of(2026, 6, 30), credit("INTEREST PAID TILL 30-JUN-2026", "612.00"));
        addIfInRange(txns, from, to, LocalDate.of(2026, 9, 30), credit("INTEREST PAID TILL 30-SEP-2026", "587.00"));
        addIfInRange(txns, from, to, LocalDate.of(2026, 8, 3), credit(
                upi("AMAZON PAY", "amazonpay@apl", "Cashback"), "150.00"));
        addIfInRange(txns, from, to, LocalDate.of(2026, 7, 19), credit(
                upi("SWIGGY", "swiggy@icici", "Refund for cancelled order"), "412.00"));

        txns.sort(Comparator.comparing(Txn::date));
        return txns;
    }

    // ---------------------------------------------------------------- credit card (alternate CSV layout)

    List<Txn> creditCard() {
        List<Txn> txns = new ArrayList<>();
        LocalDate from = LocalDate.of(2026, 9, 1);
        for (LocalDate day = from; day.isBefore(LocalDate.of(2026, 11, 1)); day = day.plusDays(1)) {
            maybe(txns, day, 0.12, () -> "BOOKMYSHOW MUMBAI IN", 300, 1400);
            maybe(txns, day, 0.10, () -> "MAKEMYTRIP INDIA PVT LTD GURGAON IN", 1800, 9000);
            maybe(txns, day, 0.15, () -> "DECATHLON SPORTS INDIA BANGALORE IN", 499, 3500);
            maybe(txns, day, 0.12, () -> "NAVARATNA JEWELLERS JAYANAGAR IN", 1500, 6000);
        }
        txns.add(debit(LocalDate.of(2026, 9, 3), "DISNEY HOTSTAR MUMBAI IN", "1499.00"));
        txns.add(credit(LocalDate.of(2026, 9, 20), "PAYMENT RECEIVED - THANK YOU", "15000.00"));
        txns.add(credit(LocalDate.of(2026, 10, 20), "PAYMENT RECEIVED - THANK YOU", "18000.00"));
        txns.sort(Comparator.comparing(Txn::date));
        return txns;
    }

    // ---------------------------------------------------------------- writers

    private static BigDecimal writeHdfcCsv(Path file, List<Txn> txns, BigDecimal opening) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yy");
        StringBuilder csv = new StringBuilder();
        csv.append("HDFC-STYLE BANK LTD (sample data - fictional)\n");
        csv.append("Account No :,XXXXXXXX4821\n");
        csv.append("Statement From :,01/05/2026,To :,31/10/2026\n\n");
        csv.append("Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance\n");
        BigDecimal balance = opening;
        Random refs = new Random(7);
        for (Txn t : txns) {
            balance = t.isDebit() ? balance.subtract(t.debit()) : balance.add(t.credit());
            String ref = String.format("%016d", Math.abs(refs.nextLong()) % 10_000_000_000_000_000L);
            csv.append(String.join(",", fmt.format(t.date()), quote(t.narration()), ref, fmt.format(t.date()),
                    t.isDebit() ? t.debit().toPlainString() : "",
                    t.isDebit() ? "" : t.credit().toPlainString(),
                    balance.toPlainString())).append('\n');
        }
        csv.append("\n*** End of Statement ***\n");
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        return balance;
    }

    private static void writeCardCsv(Path file, List<Txn> txns) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy", IN);
        StringBuilder csv = new StringBuilder("Transaction Date;Transaction Details;Amount (INR);Dr/Cr\n");
        for (Txn t : txns) {
            BigDecimal amount = t.isDebit() ? t.debit() : t.credit();
            // Indian digit grouping inside quotes, semicolon separated: exercises the CSV auto-detection.
            csv.append(fmt.format(t.date())).append(';').append(t.narration()).append(';')
                    .append('"').append(indian(amount)).append('"').append(';')
                    .append(t.isDebit() ? "Dr" : "Cr").append('\n');
        }
        Files.writeString(file, csv, StandardCharsets.UTF_8);
    }

    private static void writePdf(Path file, List<Txn> txns, BigDecimal opening) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> lines = new ArrayList<>();
        lines.add("SAMPLE BANK - STATEMENT OF ACCOUNT (fictional sample data)");
        lines.add("Account: XXXXXXXX4821    Period: 01/11/2026 to 12/11/2026");
        lines.add("Opening Balance: " + indian(opening));
        lines.add("");
        lines.add(String.format("%-11s %-52s %14s %3s %14s", "Date", "Narration", "Amount", "", "Balance"));
        BigDecimal balance = opening;
        for (Txn t : txns) {
            balance = t.isDebit() ? balance.subtract(t.debit()) : balance.add(t.credit());
            String narration = t.narration();
            String first = narration.length() > 52 ? narration.substring(0, 52) : narration;
            lines.add(String.format("%-11s %-52s %14s %3s %14s", fmt.format(t.date()), first,
                    indian(t.isDebit() ? t.debit() : t.credit()), t.isDebit() ? "Dr" : "Cr", indian(balance)));
            if (narration.length() > 52) {
                lines.add("            " + narration.substring(52)); // wrapped narration line
            }
        }
        lines.add("");
        lines.add("Closing Balance: " + indian(balance));
        lines.add("This is a computer generated statement.");

        try (PDDocument doc = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.COURIER);
            int perPage = 60;
            for (int start = 0; start < lines.size(); start += perPage) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 7.5f);
                    cs.setLeading(11f);
                    cs.newLineAtOffset(28, 800);
                    for (String line : lines.subList(start, Math.min(lines.size(), start + perPage))) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            doc.save(file.toFile());
        }
    }

    // ---------------------------------------------------------------- helpers

    private interface Narration {
        String get();
    }

    private void maybe(List<Txn> txns, LocalDate day, double probability, Narration narration, int min, int max) {
        if (random.nextDouble() < probability) {
            txns.add(debit(day, narration.get(), money(min, max)));
        }
    }

    private void addIfInRange(List<Txn> txns, LocalDate from, LocalDate to, LocalDate date, Txn template) {
        if (!date.isBefore(from) && !date.isAfter(to)) {
            txns.add(new Txn(date, template.narration(), template.debit(), template.credit()));
        }
    }

    private String upi(String payee, String vpa, String note) {
        // Banks format UPI narrations differently; alternate between the two common shapes.
        return random.nextBoolean()
                ? "UPI/" + digits(12) + "/" + payee + "/" + vpa + "/" + note
                : "UPI-" + payee + "-" + vpa.toUpperCase(IN) + "-" + "YESB0000001" + "-" + digits(12) + "-" + note.toUpperCase(IN);
    }

    private String digits(int n) {
        StringBuilder sb = new StringBuilder();
        sb.append(1 + random.nextInt(9));
        for (int i = 1; i < n; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String money(int min, int max) {
        int rupees = min + random.nextInt(max - min + 1);
        int paise = random.nextInt(4) == 0 ? random.nextInt(100) : 0;
        return BigDecimal.valueOf(rupees * 100L + paise, 2).toPlainString();
    }

    private static Txn debit(String narration, String amount) {
        return new Txn(null, narration, new BigDecimal(amount), null);
    }

    private static Txn credit(String narration, String amount) {
        return new Txn(null, narration, null, new BigDecimal(amount));
    }

    private static Txn debit(LocalDate date, String narration, String amount) {
        return new Txn(date, narration, new BigDecimal(amount), null);
    }

    private static Txn credit(LocalDate date, String narration, String amount) {
        return new Txn(date, narration, null, new BigDecimal(amount));
    }

    private static String quote(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    /** 1234567.5 -> "12,34,567.50" (Indian digit grouping). */
    static String indian(BigDecimal amount) {
        String plain = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String rupees = plain.substring(0, plain.indexOf('.'));
        String paise = plain.substring(plain.indexOf('.'));
        if (rupees.length() <= 3) {
            return rupees + paise;
        }
        String last3 = rupees.substring(rupees.length() - 3);
        StringBuilder rest = new StringBuilder(rupees.substring(0, rupees.length() - 3));
        for (int i = rest.length() - 2; i > 0; i -= 2) {
            rest.insert(i, ',');
        }
        return rest + "," + last3 + paise;
    }
}
