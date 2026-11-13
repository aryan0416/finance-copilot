package app.financecopilot.categorization;

import app.financecopilot.domain.CategorySource;

import java.util.Map;

public record CategorizationResult(Map<Integer, Assignment> assignments, Stats stats) {

    public record Assignment(Long categoryId, CategorySource source) {
    }

    /** Per-import breakdown by pipeline stage. */
    public record Stats(int byRule, int byCache, int byAi, int uncategorised) {

        public int total() {
            return byRule + byCache + byAi + uncategorised;
        }

        /** Share of rows categorised by rules or cache, i.e. without needing an LLM. */
        public double withoutAiPercent() {
            return total() == 0 ? 100.0 : Math.round(1000.0 * (byRule + byCache) / total()) / 10.0;
        }
    }
}
