package app.financecopilot.categorization;

import app.financecopilot.categorization.CategorizationResult.Assignment;
import app.financecopilot.categorization.CategorizationResult.Stats;
import app.financecopilot.domain.Category;
import app.financecopilot.domain.CategorySource;
import app.financecopilot.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the categorisation pipeline, cheapest step first:
 * <ol>
 *   <li>user's own rules</li>
 *   <li>built-in merchant rules</li>
 *   <li>shared merchant cache</li>
 *   <li>LLM, batched, only for what is still unknown</li>
 * </ol>
 * Whatever is left falls back to "Uncategorised", so categorisation can never fail an import.
 */
@Service
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);

    private final List<CategorizationStep> steps; // ordered by @Order
    private final CategoryRepository categories;

    public CategorizationService(List<CategorizationStep> steps, CategoryRepository categories) {
        this.steps = steps;
        this.categories = categories;
    }

    public CategorizationResult categorize(Long userId, List<CategorizationInput> inputs) {
        Map<Integer, Assignment> assignments = new HashMap<>();
        Map<CategorySource, Integer> counts = new EnumMap<>(CategorySource.class);
        List<CategorizationInput> pending = new ArrayList<>(inputs);

        for (CategorizationStep step : steps) {
            if (pending.isEmpty()) {
                break;
            }
            Map<Integer, Long> resolved;
            try {
                resolved = step.resolve(userId, pending);
            } catch (RuntimeException e) {
                // A failing step (e.g. the LLM) must not fail the import; later steps / fallback take over.
                log.warn("Categorisation step {} failed: {}", step.getClass().getSimpleName(), e.toString());
                continue;
            }
            resolved.forEach((index, categoryId) -> assignments.put(index, new Assignment(categoryId, step.source())));
            counts.merge(step.source(), resolved.size(), Integer::sum);
            pending.removeIf(input -> resolved.containsKey(input.index()));
        }

        if (!pending.isEmpty()) {
            Long uncategorised = categories.findByUserIdIsNullAndName(Category.UNCATEGORISED)
                    .map(Category::getId)
                    .orElseThrow(() -> new IllegalStateException("Uncategorised category missing"));
            pending.forEach(input -> assignments.put(input.index(), new Assignment(uncategorised, CategorySource.NONE)));
        }

        Stats stats = new Stats(counts.getOrDefault(CategorySource.RULE, 0), counts.getOrDefault(CategorySource.CACHE, 0),
                counts.getOrDefault(CategorySource.AI, 0), pending.size());
        log.debug("Categorised {} rows: {}", inputs.size(), stats);
        return new CategorizationResult(assignments, stats);
    }
}
