package app.financecopilot.subscription;

import app.financecopilot.domain.Frequency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Finds recurring charges in a user's debits. Pure and deterministic (no I/O, no AI):
 * <ol>
 *   <li>Group debits by normalised merchant.</li>
 *   <li>Within a merchant, take the largest cluster of charges whose amounts are within ±10% of each
 *       other, so a monthly Netflix fee is still found even if the same merchant has one-off purchases.</li>
 *   <li>Need at least 3 charges in the cluster.</li>
 *   <li>Every gap between consecutive charges must match one cadence, weekly / monthly / yearly, within
 *       ±3 days of the calendar-expected date (so 28-31 day months are all "monthly").</li>
 * </ol>
 * The next expected date is the last charge plus one period. A subscription is ACTIVE while that date
 * plus the tolerance hasn't passed the reference date (the latest date in the user's data).
 */
public final class SubscriptionDetector {

    static final int MIN_OCCURRENCES = 3;
    static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.10");
    static final int DAY_TOLERANCE = 3;

    public record Charge(String merchant, LocalDate date, BigDecimal amount) {
    }

    public record DetectedSubscription(String merchant, Frequency frequency, BigDecimal averageAmount,
                                       int occurrences, LocalDate lastChargeDate, LocalDate nextExpectedDate,
                                       boolean active) {
    }

    public List<DetectedSubscription> detect(List<Charge> debits, LocalDate referenceDate) {
        Map<String, List<Charge>> byMerchant = debits.stream().collect(Collectors.groupingBy(Charge::merchant));
        List<DetectedSubscription> found = new ArrayList<>();
        for (List<Charge> charges : byMerchant.values()) {
            if (charges.size() >= MIN_OCCURRENCES) {
                detectForMerchant(charges, referenceDate).ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparing(DetectedSubscription::nextExpectedDate)
                .thenComparing(DetectedSubscription::merchant));
        return found;
    }

    private Optional<DetectedSubscription> detectForMerchant(List<Charge> charges, LocalDate referenceDate) {
        List<Charge> cluster = largestAmountCluster(charges);
        if (cluster.size() < MIN_OCCURRENCES) {
            return Optional.empty();
        }
        cluster.sort(Comparator.comparing(Charge::date));
        for (Frequency frequency : Frequency.values()) {
            if (matchesCadence(cluster, frequency)) {
                Charge last = cluster.getLast();
                LocalDate next = advance(last.date(), frequency);
                boolean active = !next.plusDays(DAY_TOLERANCE).isBefore(referenceDate);
                BigDecimal total = cluster.stream().map(Charge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal average = total.divide(BigDecimal.valueOf(cluster.size()), 2, RoundingMode.HALF_UP);
                return Optional.of(new DetectedSubscription(last.merchant(), frequency, average, cluster.size(),
                        last.date(), next, active));
            }
        }
        return Optional.empty();
    }

    /** For each charge as anchor, collect charges within ±10% of it; keep the biggest (latest on ties). */
    static List<Charge> largestAmountCluster(List<Charge> charges) {
        List<Charge> best = List.of();
        LocalDate bestLatest = LocalDate.MIN;
        for (Charge anchor : charges) {
            BigDecimal tolerance = anchor.amount().multiply(AMOUNT_TOLERANCE);
            List<Charge> cluster = charges.stream()
                    .filter(c -> c.amount().subtract(anchor.amount()).abs().compareTo(tolerance) <= 0)
                    .collect(Collectors.toCollection(ArrayList::new));
            LocalDate latest = cluster.stream().map(Charge::date).max(Comparator.naturalOrder()).orElse(LocalDate.MIN);
            if (cluster.size() > best.size() || (cluster.size() == best.size() && latest.isAfter(bestLatest))) {
                best = cluster;
                bestLatest = latest;
            }
        }
        return new ArrayList<>(best);
    }

    static boolean matchesCadence(List<Charge> sorted, Frequency frequency) {
        for (int i = 1; i < sorted.size(); i++) {
            LocalDate expected = advance(sorted.get(i - 1).date(), frequency);
            long drift = Math.abs(ChronoUnit.DAYS.between(expected, sorted.get(i).date()));
            if (drift > DAY_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    static LocalDate advance(LocalDate date, Frequency frequency) {
        return switch (frequency) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case YEARLY -> date.plusYears(1);
        };
    }
}
