package app.financecopilot.dto;

import app.financecopilot.domain.Frequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(YearMonth month, List<YearMonth> availableMonths, Kpis kpis,
                                    List<MonthlyTotal> monthly, List<CategorySpend> categories,
                                    List<MerchantSpend> topMerchants, List<Anomaly> anomalies,
                                    List<UpcomingCharge> upcoming, CategorisationStats categorisation) {
    }

    /**
     * Money KPIs for the month. {@code spent} is net of refunds and excludes transfers/investments;
     * {@code savingsRate} = (income − spent) / income, null when there is no income.
     */
    public record Kpis(BigDecimal spent, BigDecimal income, Double savingsRate, BigDecimal previousSpent,
                       Double spentChangePercent, long transactionCount) {
    }

    public record MonthlyTotal(YearMonth month, BigDecimal income, BigDecimal spent, Double spentChangePercent) {
    }

    public record CategorySpend(Long categoryId, String name, String color, BigDecimal amount, long count) {
    }

    public record MerchantSpend(String merchant, BigDecimal amount, long count) {
    }

    /** A debit above mean + 2σ of the user's debits in the same category. */
    public record Anomaly(Long transactionId, LocalDate date, String merchant, String category, BigDecimal amount,
                          BigDecimal categoryAverage, BigDecimal threshold) {
    }

    public record UpcomingCharge(String merchant, BigDecimal amount, LocalDate expectedDate, Frequency frequency) {
    }

    /** All-time breakdown of how this user's transactions were categorised (the cost-optimisation metric). */
    public record CategorisationStats(long total, long byRule, long byCache, long byAi, long byUser,
                                      long uncategorised, double autoCategorisedPercent, double withoutAiPercent,
                                      long aiCalls) {
    }
}
