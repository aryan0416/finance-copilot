package app.financecopilot.ai.sql;

import app.financecopilot.ai.LlmClient;
import app.financecopilot.ai.LlmClient.LlmRequest;
import app.financecopilot.ai.LlmClient.LlmResponse;
import app.financecopilot.ai.LlmJson;
import app.financecopilot.ai.LlmUnavailableException;
import app.financecopilot.ai.sql.SafeQueryExecutor.QueryResult;
import app.financecopilot.ai.sql.SqlValidator.ValidatedQuery;
import app.financecopilot.domain.AiQueryLog;
import app.financecopilot.domain.Category;
import app.financecopilot.dto.AskDtos.AskResponse;
import app.financecopilot.dto.AskDtos.ChartType;
import app.financecopilot.exception.ApiException;
import app.financecopilot.repository.AiQueryLogRepository;
import app.financecopilot.repository.CategoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Plain-English question → SQL → validated → executed against the caller's rows only.
 * The model sees a single virtual table and today's date; it never sees real table names, other users'
 * data or row values. If the first SQL fails validation or execution, the error is fed back once for a
 * repair attempt. Every attempt is written to {@code ai_query_log}.
 */
@Service
public class TextToSqlService {

    private static final Logger log = LoggerFactory.getLogger(TextToSqlService.class);

    private static final String SYSTEM_PROMPT = """
            You translate questions about a person's bank transactions into a single PostgreSQL SELECT.
            The ONLY table is:
              my_transactions(date DATE, merchant TEXT, description TEXT, amount NUMERIC, direction TEXT, category TEXT)
            - amount is always positive; direction is 'DEBIT' (money out / spending) or 'CREDIT' (money in).
            - "spend"/"spent" means direction = 'DEBIT'. "income"/"received" means direction = 'CREDIT'.
            - merchant is a clean name like 'Swiggy', 'Uber', 'Netflix'; match it with ILIKE '%%name%%'.
            - category is one of: %s.
            - Today is %s. Currency is INR. Resolve relative dates ("last 3 months", "October") from today;
              a month name without a year means its most recent occurrence.
            Rules: one SELECT only; no WITH, no comments, no other tables; use only common functions
            (sum, count, avg, min, max, round, date_trunc, to_char, extract, coalesce, lower, upper).
            Give columns readable aliases. For time series return a month label like to_char(date_trunc('month', date), 'YYYY-MM') AS month and ORDER BY it.
            chartType: "number" for a single value, "line" for a trend over time, "pie" for shares of a
            whole (<= 8 slices), "bar" for comparing categories/merchants, "table" for transaction lists.
            Respond with ONLY JSON: {"sql":"...","chartType":"bar|line|pie|table|number","explanation":"one short sentence"}""";

    private final LlmClient llm;
    private final SqlValidator validator;
    private final SafeQueryExecutor executor;
    private final AiQueryLogRepository aiLog;
    private final CategoryRepository categories;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TextToSqlService(LlmClient llm, SqlValidator validator, SafeQueryExecutor executor,
                            AiQueryLogRepository aiLog, CategoryRepository categories, ObjectMapper mapper,
                            Clock clock) {
        this.llm = llm;
        this.validator = validator;
        this.executor = executor;
        this.aiLog = aiLog;
        this.categories = categories;
        this.mapper = mapper;
        this.clock = clock;
    }

    public AskResponse ask(Long userId, String question) {
        String categoryNames = categories.findVisibleTo(userId).stream().map(Category::getName)
                .map(n -> "'" + n.replace("'", "''") + "'").collect(Collectors.joining(", "));
        String system = SYSTEM_PROMPT.formatted(categoryNames, LocalDate.now(clock));
        long started = System.nanoTime();

        String prompt = "Question: " + question;
        String lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            AiQueryLog entry = new AiQueryLog(userId, AiQueryLog.Kind.ASK, question);
            try {
                LlmResponse response = llm.complete(new LlmRequest(system, prompt));
                entry.usage(response.latencyMs(), response.promptTokens(), response.completionTokens());
                Generated generated = parse(response.text());
                entry.generatedSql(generated.sql());

                ValidatedQuery validated = validator.validate(generated.sql());
                entry.generatedSql(validated.sql());
                QueryResult result = executor.execute(userId, validated);
                entry.outcome(true, null, result.rows().size());
                long latencyMs = (System.nanoTime() - started) / 1_000_000;
                return new AskResponse(question, validated.sql(), chartTypeFor(generated.chartType(), result),
                        generated.explanation(), result.columns(), result.rows(), latencyMs);
            } catch (UnsafeSqlException e) {
                lastError = "Rejected by validator: " + e.getMessage();
                entry.outcome(false, lastError, null);
                log.info("Ask-AI SQL rejected for user {}: {}", userId, e.getMessage());
            } catch (DataAccessException e) {
                lastError = "Query failed: " + rootMessage(e);
                entry.outcome(false, lastError, null);
            } catch (LlmUnavailableException e) {
                entry.outcome(false, e.getMessage(), null);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The AI assistant is unavailable right now. Please try again in a minute.");
            } finally {
                aiLog.save(entry); // successes, rejections and failures alike
            }
            // One repair round: show the model what went wrong with its previous answer.
            prompt = "Question: " + question + "\nYour previous SQL could not be used (" + lastError
                    + "). Return corrected JSON following all rules.";
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "I couldn't turn that into a safe query. Try rephrasing. (" + lastError + ")");
    }

    private record Generated(String sql, ChartType chartType, String explanation) {
    }

    private Generated parse(String text) {
        JsonNode json = LlmJson.parseObject(mapper, text);
        String sql = json.path("sql").asText("");
        if (sql.isBlank()) {
            throw new UnsafeSqlException("Model returned no SQL");
        }
        ChartType chartType;
        try {
            chartType = ChartType.valueOf(json.path("chartType").asText("table").toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            chartType = ChartType.table;
        }
        return new Generated(sql, chartType, json.path("explanation").asText(""));
    }

    /** Guard against chart types the result shape can't support. */
    static ChartType chartTypeFor(ChartType requested, QueryResult result) {
        int columns = result.columns().size();
        int rows = result.rows().size();
        if (rows == 1 && columns == 1) {
            return ChartType.number;
        }
        if (requested == ChartType.number || columns < 2 || rows == 0) {
            return ChartType.table;
        }
        if (requested == ChartType.pie && rows > 8) {
            return ChartType.bar;
        }
        return requested;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
