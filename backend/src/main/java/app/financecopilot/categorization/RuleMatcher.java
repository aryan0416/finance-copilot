package app.financecopilot.categorization;

import app.financecopilot.domain.CategoryRule;
import app.financecopilot.domain.MatchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compiles rules once per import and returns the highest-priority (lowest number) matching category. */
final class RuleMatcher {

    private static final Logger log = LoggerFactory.getLogger(RuleMatcher.class);

    private record CompiledRule(Long categoryId, int priority, long id, Predicate<String> predicate) {
    }

    private final List<CompiledRule> rules;

    RuleMatcher(List<CategoryRule> rules) {
        this.rules = rules.stream()
                .map(RuleMatcher::compile)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(CompiledRule::priority).thenComparingLong(CompiledRule::id))
                .toList();
    }

    Optional<Long> match(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (CompiledRule rule : rules) {
            if (rule.predicate().test(lower)) {
                return Optional.of(rule.categoryId());
            }
        }
        return Optional.empty();
    }

    boolean isEmpty() {
        return rules.isEmpty();
    }

    private static Optional<CompiledRule> compile(CategoryRule rule) {
        Predicate<String> predicate;
        if (rule.getMatchType() == MatchType.REGEX) {
            try {
                predicate = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE).asPredicate();
            } catch (PatternSyntaxException e) {
                log.warn("Skipping rule {} with invalid regex", rule.getId());
                return Optional.empty();
            }
        } else {
            String keyword = rule.getPattern().toLowerCase(Locale.ROOT);
            predicate = text -> text.contains(keyword);
        }
        long id = rule.getId() == null ? Long.MAX_VALUE : rule.getId();
        return Optional.of(new CompiledRule(rule.getCategoryId(), rule.getPriority(), id, predicate));
    }
}
