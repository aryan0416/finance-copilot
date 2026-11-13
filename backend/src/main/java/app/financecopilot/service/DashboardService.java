package app.financecopilot.service;

import app.financecopilot.domain.AiQueryLog;
import app.financecopilot.domain.Frequency;
import app.financecopilot.dto.DashboardDtos.Anomaly;
import app.financecopilot.dto.DashboardDtos.CategorisationStats;
import app.financecopilot.dto.DashboardDtos.CategorySpend;
import app.financecopilot.dto.DashboardDtos.DashboardResponse;
import app.financecopilot.dto.DashboardDtos.Kpis;
import app.financecopilot.dto.DashboardDtos.MerchantSpend;
import app.financecopilot.dto.DashboardDtos.MonthlyTotal;
import app.financecopilot.dto.DashboardDtos.UpcomingCharge;
import app.financecopilot.repository.AiQueryLogRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard aggregates computed in Postgres. Every query is parameterised by {@code :userId}.
 * Money semantics: transfers/investments (category kind TRANSFER) are neither income nor spending;
 * credits in expense categories (refunds) reduce spending; income is credits in INCOME categories or
 * not yet categorised.
 */
@Service
public class DashboardService {

    static final int TREND_MONTHS = 6;
    static final int ANOMALY_LOOKBACK_MONTHS = 3;
    static final int UPCOMING_DAYS = 30;

    /** Per-row signed spend: debits add, refunds in expense categories subtract. */
    private static final String NET_SPEND = """
            CASE WHEN COALESCE(c.kind, 'EXPENSE') <> 'EXPENSE' THEN 0
                 WHEN t.direction = 'DEBIT' THEN t.amount
                 WHEN c.id IS NULL OR c.name = 'Uncategorised' THEN 0
                 ELSE -t.amount END""";
    private static final String INCOME = """
            CASE WHEN t.direction = 'CREDIT' AND (c.kind = 'INCOME' OR c.name = 'Uncategorised' OR c.id IS NULL)
                 THEN t.amount ELSE 0 END""";
    private static final String FROM = """
            FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId""";

    private final NamedParameterJdbcTemplate jdbc;
    private final AiQueryLogRepository aiLog;

    public DashboardService(NamedParameterJdbcTemplate jdbc, AiQueryLogRepository aiLog) {
        this.jdbc = jdbc;
        this.aiLog = aiLog;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long userId, YearMonth requestedMonth) {
        List<YearMonth> months = availableMonths(userId);
        YearMonth month = requestedMonth != null ? requestedMonth
                : months.isEmpty() ? YearMonth.now() : months.getFirst();

        List<MonthlyTotal> monthly = monthlyTotals(userId, month);
        MonthlyTotal current = monthly.getLast();
        MonthlyTotal previous = monthly.size() > 1 ? monthly.get(monthly.size() - 2) : null;
        Kpis kpis = new Kpis(current.spent(), current.income(), savingsRate(current), previous == null ? null : previous.spent(),
                current.spentChangePercent(), transactionCount(userId, month));

        return new DashboardResponse(month, months, kpis, monthly, categorySpend(userId, month),
                topMerchants(userId, month, 5), anomalies(userId, month), upcoming(userId), categorisationStats(userId));
    }

    List<YearMonth> availableMonths(Long userId) {
        return jdbc.query("SELECT DISTINCT date_trunc('month', t.txn_date)::date AS m " + FROM + " ORDER BY m DESC",
                params(userId), (rs, n) -> YearMonth.from(rs.getDate("m").toLocalDate()));
    }

    List<MonthlyTotal> monthlyTotals(Long userId, YearMonth endMonth) {
        YearMonth start = endMonth.minusMonths(TREND_MONTHS - 1);
        Map<YearMonth, BigDecimal[]> rows = new HashMap<>();
        jdbc.query("SELECT date_trunc('month', t.txn_date)::date AS m, SUM(" + INCOME + ") AS income, SUM(" + NET_SPEND
                        + ") AS spent " + FROM + " AND t.txn_date >= :start AND t.txn_date < :end GROUP BY m",
                params(userId).addValue("start", Date.valueOf(start.atDay(1)))
                        .addValue("end", Date.valueOf(endMonth.plusMonths(1).atDay(1))),
                rs -> {
                    rows.put(YearMonth.from(rs.getDate("m").toLocalDate()),
                            new BigDecimal[]{rs.getBigDecimal("income"), rs.getBigDecimal("spent")});
                });
        List<MonthlyTotal> result = new ArrayList<>();
        BigDecimal previousSpent = null;
        for (YearMonth m = start; !m.isAfter(endMonth); m = m.plusMonths(1)) {
            BigDecimal[] v = rows.getOrDefault(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal spent = v[1].max(BigDecimal.ZERO);
            result.add(new MonthlyTotal(m, v[0], spent, percentChange(previousSpent, spent)));
            previousSpent = spent;
        }
        return result;
    }

    List<CategorySpend> categorySpend(Long userId, YearMonth month) {
        return jdbc.query("SELECT c.id, c.name, c.color, SUM(" + NET_SPEND + ") AS amount, COUNT(*) FILTER "
                        + "(WHERE t.direction = 'DEBIT') AS cnt " + FROM + " AND t.txn_date >= :start AND t.txn_date < :end "
                        + "GROUP BY c.id, c.name, c.color HAVING SUM(" + NET_SPEND + ") > 0 ORDER BY amount DESC",
                monthParams(userId, month),
                (rs, n) -> new CategorySpend(rs.getLong("id"), rs.getString("name"), rs.getString("color"),
                        rs.getBigDecimal("amount"), rs.getLong("cnt")));
    }

    List<MerchantSpend> topMerchants(Long userId, YearMonth month, int limit) {
        return jdbc.query("SELECT t.merchant_normalized AS merchant, SUM(t.amount) AS amount, COUNT(*) AS cnt " + FROM
                        + " AND t.direction = 'DEBIT' AND COALESCE(c.kind, 'EXPENSE') = 'EXPENSE'"
                        + " AND t.txn_date >= :start AND t.txn_date < :end"
                        + " GROUP BY t.merchant_normalized ORDER BY amount DESC LIMIT :limit",
                monthParams(userId, month).addValue("limit", limit),
                (rs, n) -> new MerchantSpend(rs.getString("merchant"), rs.getBigDecimal("amount"), rs.getLong("cnt")));
    }

    /** Debits in the last few months above mean + 2·stddev of the user's debits in that category. */
    List<Anomaly> anomalies(Long userId, YearMonth month) {
        return jdbc.query("""
                        WITH stats AS (
                            SELECT category_id, AVG(amount) AS mean, STDDEV_SAMP(amount) AS sd, COUNT(*) AS n
                            FROM transactions WHERE user_id = :userId AND direction = 'DEBIT'
                            GROUP BY category_id)
                        SELECT t.id, t.txn_date, t.merchant_normalized, c.name, t.amount, s.mean, s.mean + 2 * s.sd AS threshold
                        FROM transactions t
                        JOIN stats s ON s.category_id = t.category_id
                        JOIN categories c ON c.id = t.category_id
                        WHERE t.user_id = :userId AND t.direction = 'DEBIT' AND s.n >= 5 AND s.sd > 0
                          AND c.kind = 'EXPENSE' AND t.amount > s.mean + 2 * s.sd
                          AND t.txn_date >= :start AND t.txn_date < :end
                        ORDER BY t.amount DESC LIMIT 10""",
                params(userId)
                        .addValue("start", Date.valueOf(month.minusMonths(ANOMALY_LOOKBACK_MONTHS - 1).atDay(1)))
                        .addValue("end", Date.valueOf(month.plusMonths(1).atDay(1))),
                (rs, n) -> new Anomaly(rs.getLong("id"), rs.getDate("txn_date").toLocalDate(),
                        rs.getString("merchant_normalized"), rs.getString("name"), rs.getBigDecimal("amount"),
                        scale(rs.getBigDecimal("mean")), scale(rs.getBigDecimal("threshold"))));
    }

    /** Active subscriptions due within 30 days of the latest date in the user's data. */
    List<UpcomingCharge> upcoming(Long userId) {
        return jdbc.query("""
                        SELECT s.merchant_normalized, s.avg_amount, s.next_expected_date, s.frequency
                        FROM subscriptions s
                        WHERE s.user_id = :userId AND s.status = 'ACTIVE'
                          AND s.next_expected_date <= (SELECT max(txn_date) FROM transactions WHERE user_id = :userId) + :days
                        ORDER BY s.next_expected_date""",
                params(userId).addValue("days", UPCOMING_DAYS),
                (rs, n) -> new UpcomingCharge(rs.getString(1), rs.getBigDecimal(2), rs.getDate(3).toLocalDate(),
                        Frequency.valueOf(rs.getString(4))));
    }

    CategorisationStats categorisationStats(Long userId) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("SELECT category_source, COUNT(*) AS n FROM transactions WHERE user_id = :userId GROUP BY category_source",
                params(userId), rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                });
        long rule = counts.getOrDefault("RULE", 0L);
        long cache = counts.getOrDefault("CACHE", 0L);
        long ai = counts.getOrDefault("AI", 0L);
        long user = counts.getOrDefault("USER", 0L);
        long none = counts.getOrDefault("NONE", 0L);
        long total = rule + cache + ai + user + none;
        return new CategorisationStats(total, rule, cache, ai, user, none,
                percent(rule + cache + ai, total), percent(rule + cache, total),
                aiLog.countByUserIdAndKind(userId, AiQueryLog.Kind.CATEGORIZE));
    }

    private long transactionCount(Long userId, YearMonth month) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) " + FROM + " AND t.txn_date >= :start AND t.txn_date < :end",
                monthParams(userId, month), Long.class);
        return n == null ? 0 : n;
    }

    private static MapSqlParameterSource params(Long userId) {
        return new MapSqlParameterSource("userId", userId);
    }

    private static MapSqlParameterSource monthParams(Long userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        return params(userId).addValue("start", Date.valueOf(start)).addValue("end", Date.valueOf(start.plusMonths(1)));
    }

    private static Double savingsRate(MonthlyTotal m) {
        if (m.income().signum() <= 0) {
            return null;
        }
        return round1(100.0 * m.income().subtract(m.spent()).doubleValue() / m.income().doubleValue());
    }

    private static Double percentChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return round1(100.0 * current.subtract(previous).doubleValue() / previous.doubleValue());
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : round1(100.0 * part / total);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
