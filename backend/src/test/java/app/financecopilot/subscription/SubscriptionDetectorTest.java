package app.financecopilot.subscription;

import app.financecopilot.domain.Frequency;
import app.financecopilot.subscription.SubscriptionDetector.Charge;
import app.financecopilot.subscription.SubscriptionDetector.DetectedSubscription;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionDetectorTest {

    private final SubscriptionDetector detector = new SubscriptionDetector();

    private static Charge charge(String merchant, String date, String amount) {
        return new Charge(merchant, LocalDate.parse(date), new BigDecimal(amount));
    }

    private List<DetectedSubscription> detect(List<Charge> charges, String reference) {
        return detector.detect(charges, LocalDate.parse(reference));
    }

    @Nested
    class Cadence {

        @Test
        void detectsMonthlyChargeOnSameDay() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Netflix", "2026-07-05", "649"), charge("Netflix", "2026-08-05", "649"),
                    charge("Netflix", "2026-09-05", "649"), charge("Netflix", "2026-10-05", "649")), "2026-10-20");

            assertThat(result).singleElement().satisfies(s -> {
                assertThat(s.frequency()).isEqualTo(Frequency.MONTHLY);
                assertThat(s.occurrences()).isEqualTo(4);
                assertThat(s.averageAmount()).isEqualByComparingTo("649.00");
                assertThat(s.lastChargeDate()).isEqualTo(LocalDate.parse("2026-10-05"));
                assertThat(s.nextExpectedDate()).isEqualTo(LocalDate.parse("2026-11-05"));
                assertThat(s.active()).isTrue();
            });
        }

        @Test
        void monthlyToleratesThreeDaysDriftAndShortMonths() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Gym", "2026-01-31", "1499"), charge("Gym", "2026-02-28", "1499"), // Feb: 28 days
                    charge("Gym", "2026-03-31", "1499"), charge("Gym", "2026-05-02", "1499")), // +2 days drift
                    "2026-05-10");

            assertThat(result).singleElement().extracting(DetectedSubscription::frequency).isEqualTo(Frequency.MONTHLY);
        }

        @Test
        void rejectsDriftBeyondThreeDays() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Gym", "2026-01-10", "1499"), charge("Gym", "2026-02-10", "1499"),
                    charge("Gym", "2026-03-15", "1499")), "2026-03-20"); // +5 days

            assertThat(result).isEmpty();
        }

        @Test
        void detectsWeekly() {
            List<Charge> charges = new ArrayList<>();
            for (LocalDate d = LocalDate.parse("2026-09-06"); d.isBefore(LocalDate.parse("2026-10-31")); d = d.plusWeeks(1)) {
                charges.add(new Charge("Daily Fresh Dairy", d, new BigDecimal("420")));
            }
            assertThat(detect(charges, "2026-10-31")).singleElement().satisfies(s -> {
                assertThat(s.frequency()).isEqualTo(Frequency.WEEKLY);
                assertThat(s.occurrences()).isEqualTo(8);
            });
        }

        @Test
        void detectsYearly() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Amazon Prime", "2023-08-14", "1499"), charge("Amazon Prime", "2024-08-15", "1499"),
                    charge("Amazon Prime", "2025-08-13", "1499")), "2025-12-01");

            assertThat(result).singleElement().satisfies(s -> {
                assertThat(s.frequency()).isEqualTo(Frequency.YEARLY);
                assertThat(s.nextExpectedDate()).isEqualTo(LocalDate.parse("2026-08-13"));
            });
        }

        @Test
        void irregularIntervalsAreNotASubscription() {
            assertThat(detect(List.of(
                    charge("Swiggy", "2026-10-01", "400"), charge("Swiggy", "2026-10-04", "410"),
                    charge("Swiggy", "2026-10-19", "395"), charge("Swiggy", "2026-10-21", "405")), "2026-10-31"))
                    .isEmpty();
        }
    }

    @Nested
    class AmountsAndOccurrences {

        @Test
        void requiresAtLeastThreeOccurrences() {
            assertThat(detect(List.of(
                    charge("Spotify", "2026-09-12", "119"), charge("Spotify", "2026-10-12", "119")), "2026-10-20"))
                    .isEmpty();
        }

        @Test
        void allowsTenPercentAmountVariation() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Airtel", "2026-07-15", "1000"), charge("Airtel", "2026-08-15", "1090"),
                    charge("Airtel", "2026-09-15", "950")), "2026-09-20");

            assertThat(result).singleElement().extracting(DetectedSubscription::averageAmount)
                    .satisfies(avg -> assertThat(avg).isEqualByComparingTo("1013.33"));
        }

        @Test
        void amountsVaryingMoreThanTenPercentAreNotASubscription() {
            // Electricity: monthly but variable; must not be reported as a fixed subscription.
            assertThat(detect(List.of(
                    charge("BESCOM", "2026-07-18", "1100"), charge("BESCOM", "2026-08-18", "2400"),
                    charge("BESCOM", "2026-09-18", "1650")), "2026-09-30"))
                    .isEmpty();
        }

        @Test
        void findsSubscriptionAmongOneOffPurchasesAtSameMerchant() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Amazon", "2026-07-03", "2999"), charge("Amazon", "2026-07-09", "129"),
                    charge("Amazon", "2026-08-09", "129"), charge("Amazon", "2026-08-21", "4599"),
                    charge("Amazon", "2026-09-09", "129")), "2026-09-15");

            assertThat(result).singleElement().satisfies(s -> {
                assertThat(s.averageAmount()).isEqualByComparingTo("129.00");
                assertThat(s.occurrences()).isEqualTo(3);
            });
        }
    }

    @Nested
    class Status {

        @Test
        void inactiveWhenExpectedChargeIsOverdueBeyondTolerance() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Hotstar", "2026-03-03", "299"), charge("Hotstar", "2026-04-03", "299"),
                    charge("Hotstar", "2026-05-03", "299")), "2026-07-01"); // June 3 charge never came

            assertThat(result).singleElement().extracting(DetectedSubscription::active).isEqualTo(false);
        }

        @Test
        void stillActiveWithinToleranceOfExpectedDate() {
            List<DetectedSubscription> result = detect(List.of(
                    charge("Hotstar", "2026-03-03", "299"), charge("Hotstar", "2026-04-03", "299"),
                    charge("Hotstar", "2026-05-03", "299")), "2026-06-06");

            assertThat(result).singleElement().extracting(DetectedSubscription::active).isEqualTo(true);
        }
    }

    @Test
    void groupsByMerchantAndSortsByNextExpectedDate() {
        List<DetectedSubscription> result = detect(List.of(
                charge("Spotify", "2026-08-12", "119"), charge("Spotify", "2026-09-12", "119"),
                charge("Spotify", "2026-10-12", "119"),
                charge("Netflix", "2026-08-05", "649"), charge("Netflix", "2026-09-05", "649"),
                charge("Netflix", "2026-10-05", "649"),
                charge("Zomato", "2026-10-01", "300")), "2026-10-20");

        assertThat(result).extracting(DetectedSubscription::merchant).containsExactly("Netflix", "Spotify");
    }

    @Test
    void sameDayDuplicatesBreakTheCadence() {
        assertThat(detect(List.of(
                charge("Jio", "2026-08-20", "349"), charge("Jio", "2026-08-20", "349"),
                charge("Jio", "2026-09-20", "349")), "2026-09-25"))
                .isEmpty();
    }
}
