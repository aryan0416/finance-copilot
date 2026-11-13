package app.financecopilot.service;

import app.financecopilot.ai.LlmClient;
import app.financecopilot.ai.LlmClient.LlmRequest;
import app.financecopilot.ai.LlmClient.LlmResponse;
import app.financecopilot.ai.LlmUnavailableException;
import app.financecopilot.domain.AiQueryLog;
import app.financecopilot.dto.AskDtos.InsightResponse;
import app.financecopilot.dto.DashboardDtos.CategorySpend;
import app.financecopilot.dto.DashboardDtos.MerchantSpend;
import app.financecopilot.dto.DashboardDtos.MonthlyTotal;
import app.financecopilot.exception.ApiException;
import app.financecopilot.repository.AiQueryLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Monthly plain-English summary. Privacy by construction: the model receives only aggregates
 * (totals, category sums, top merchants, recurring cost), never individual transactions or narrations.
 * Results are cached per user + month + data version, so revisiting the page costs no extra AI calls.
 */
@Service
public class InsightService {

    private static final String SYSTEM_PROMPT = """
            You are a friendly, concise personal-finance assistant for an Indian user. Using ONLY the JSON
            figures provided (amounts in INR), write one paragraph of 3-5 sentences, under 110 words:
            how the month went overall, the biggest category and notable changes vs last month, and one
            specific, practical suggestion. Use ₹ with Indian digit grouping. No headings, no bullet points,
            no markdown, and do not invent numbers that are not in the data. If partialMonthDataThrough is
            present, the month is incomplete: say so, and do not present the lower total as a real drop.""";

    private final DashboardService dashboard;
    private final SubscriptionService subscriptions;
    private final LlmClient llm;
    private final AiQueryLogRepository aiLog;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<String, InsightResponse> cache = new ConcurrentHashMap<>();

    public InsightService(DashboardService dashboard, SubscriptionService subscriptions, LlmClient llm,
                          AiQueryLogRepository aiLog, ObjectMapper mapper, Clock clock) {
        this.dashboard = dashboard;
        this.subscriptions = subscriptions;
        this.llm = llm;
        this.aiLog = aiLog;
        this.mapper = mapper;
        this.clock = clock;
    }

    public InsightResponse monthly(Long userId, YearMonth requested, boolean refresh) {
        List<YearMonth> months = dashboard.availableMonths(userId);
        if (months.isEmpty()) {
            throw ApiException.badRequest("Upload a statement first; there is nothing to summarise yet");
        }
        YearMonth month = requested != null ? requested : months.getFirst();
        Map<String, Object> figures = figures(userId, month);
        String cacheKey = userId + ":" + month + ":" + figures.hashCode();
        if (!refresh && cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        AiQueryLog entry = new AiQueryLog(userId, AiQueryLog.Kind.INSIGHT, "Monthly insight " + month);
        try {
            LlmResponse response = llm.complete(new LlmRequest(SYSTEM_PROMPT, "Figures: " + toJson(figures)));
            entry.usage(response.latencyMs(), response.promptTokens(), response.completionTokens()).outcome(true, null, null);
            InsightResponse insight = new InsightResponse(month, response.text().strip(), figures, clock.instant());
            cache.put(cacheKey, insight);
            return insight;
        } catch (LlmUnavailableException e) {
            entry.outcome(false, e.getMessage(), null);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The AI assistant is unavailable right now. Please try again shortly.");
        } finally {
            aiLog.save(entry);
        }
    }

    /** The complete, aggregate-only payload the model sees (also returned to the UI for transparency). */
    Map<String, Object> figures(Long userId, YearMonth month) {
        List<MonthlyTotal> monthly = dashboard.monthlyTotals(userId, month);
        MonthlyTotal current = monthly.getLast();
        MonthlyTotal previous = monthly.size() > 1 ? monthly.get(monthly.size() - 2) : null;

        Map<String, BigDecimal> lastMonthByCategory = dashboard.categorySpend(userId, month.minusMonths(1)).stream()
                .collect(Collectors.toMap(CategorySpend::name, CategorySpend::amount));
        List<Map<String, Object>> categories = new ArrayList<>();
        for (CategorySpend c : dashboard.categorySpend(userId, month).stream().limit(6).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", c.name());
            row.put("spent", c.amount());
            row.put("lastMonth", lastMonthByCategory.getOrDefault(c.name(), BigDecimal.ZERO));
            categories.add(row);
        }

        var recurring = subscriptions.list(userId).stream().filter(s -> s.status().name().equals("ACTIVE")).toList();
        BigDecimal recurringMonthly = recurring.stream()
                .map(s -> s.yearlyCost().divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("month", month.toString());
        LocalDate lastDate = dashboard.lastDateInMonth(userId, month);
        if (lastDate != null && lastDate.isBefore(month.atEndOfMonth())) {
            // Partial month: comparing raw totals with a full previous month would be misleading.
            figures.put("partialMonthDataThrough", lastDate.toString());
        }
        figures.put("income", current.income());
        figures.put("spent", current.spent());
        figures.put("saved", current.income().subtract(current.spent()));
        figures.put("previousMonthSpent", previous == null ? null : previous.spent());
        figures.put("spentChangePercent", current.spentChangePercent());
        figures.put("topCategories", categories);
        figures.put("topMerchants", dashboard.topMerchants(userId, month, 3).stream()
                .collect(Collectors.toMap(MerchantSpend::merchant, MerchantSpend::amount, (a, b) -> a, LinkedHashMap::new)));
        figures.put("recurringPaymentsCount", recurring.size());
        figures.put("recurringMonthlyCost", recurringMonthly);
        figures.put("unusualSpends", dashboard.anomalies(userId, month).stream()
                .filter(a -> YearMonth.from(a.date()).equals(month))
                .map(a -> Map.of("merchant", a.merchant(), "amount", a.amount()))
                .toList());
        return figures;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
