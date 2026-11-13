package app.financecopilot.categorization;

import app.financecopilot.domain.CategorySource;

import java.util.List;
import java.util.Map;

/**
 * One stage of the categorisation pipeline. Steps run cheapest-first; each only sees the inputs that
 * earlier steps could not resolve, so the expensive LLM step receives the smallest possible set.
 */
public interface CategorizationStep {

    /** Recorded on each transaction this step categorises. */
    CategorySource source();

    /** @return category id per {@link CategorizationInput#index()} for the inputs this step resolved. */
    Map<Integer, Long> resolve(Long userId, List<CategorizationInput> pending);
}
