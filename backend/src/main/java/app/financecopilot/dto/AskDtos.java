package app.financecopilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public final class AskDtos {

    private AskDtos() {
    }

    public enum ChartType { bar, line, pie, table, number }

    public record AskRequest(@NotBlank @Size(max = 500) String question) {
    }

    public record AskResponse(String question, String sql, ChartType chartType, String explanation,
                              List<String> columns, List<List<Object>> rows, long latencyMs) {
    }

    /** Monthly summary; {@code figures} are the aggregates the model saw (nothing else is sent). */
    public record InsightResponse(YearMonth month, String summary, Map<String, Object> figures,
                                  Instant generatedAt) {
    }

    public record CategoryDelta(String category, BigDecimal thisMonth, BigDecimal lastMonth) {
    }
}
