package app.financecopilot.dto;

import app.financecopilot.domain.FileType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StatementDtos {

    private StatementDtos() {
    }

    public record ImportSummary(Long statementId, String fileName, FileType fileType,
                                int imported, int duplicates, int failed, List<FailedRow> failedRows,
                                LocalDate periodStart, LocalDate periodEnd, CategorizationSummary categorization) {
    }

    public record FailedRow(int line, String reason) {
    }

    /** How the imported rows were categorised; {@code withoutAiPercent} is the cost-optimisation metric. */
    public record CategorizationSummary(int byRule, int byCache, int byAi, int uncategorised,
                                        double withoutAiPercent) {
    }

    public record StatementResponse(Long id, String fileName, FileType fileType, int imported, int duplicates,
                                    int failed, LocalDate periodStart, LocalDate periodEnd, Instant uploadedAt) {
    }
}
