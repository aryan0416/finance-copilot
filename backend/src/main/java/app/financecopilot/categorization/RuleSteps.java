package app.financecopilot.categorization;

import app.financecopilot.domain.CategoryRule;
import app.financecopilot.domain.CategorySource;
import app.financecopilot.repository.CategoryRuleRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Steps (a) and (b) of the pipeline: rule matching, no network, no AI. */
final class RuleSteps {

    private RuleSteps() {
    }

    static Map<Integer, Long> apply(List<CategoryRule> rules, List<CategorizationInput> pending) {
        RuleMatcher matcher = new RuleMatcher(rules);
        Map<Integer, Long> resolved = new HashMap<>();
        if (matcher.isEmpty()) {
            return resolved;
        }
        for (CategorizationInput input : pending) {
            matcher.match(input.matchText()).ifPresent(categoryId -> resolved.put(input.index(), categoryId));
        }
        return resolved;
    }

    /** (a) The user's own rules, created from manual recategorisations. They override everything else. */
    @Component
    @Order(10)
    static class UserRuleStep implements CategorizationStep {

        private final CategoryRuleRepository rules;

        UserRuleStep(CategoryRuleRepository rules) {
            this.rules = rules;
        }

        @Override
        public CategorySource source() {
            return CategorySource.RULE;
        }

        @Override
        public Map<Integer, Long> resolve(Long userId, List<CategorizationInput> pending) {
            return apply(rules.findByUserIdOrderByPriorityAscIdAsc(userId), pending);
        }
    }

    /** (b) Built-in keyword/regex rules for well-known Indian merchants (seeded by Flyway). */
    @Component
    @Order(20)
    static class BuiltInRuleStep implements CategorizationStep {

        private final CategoryRuleRepository rules;

        BuiltInRuleStep(CategoryRuleRepository rules) {
            this.rules = rules;
        }

        @Override
        public CategorySource source() {
            return CategorySource.RULE;
        }

        @Override
        public Map<Integer, Long> resolve(Long userId, List<CategorizationInput> pending) {
            return apply(rules.findByUserIdIsNullOrderByPriorityAscIdAsc(), pending);
        }
    }
}
