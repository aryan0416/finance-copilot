package app.financecopilot.categorization;

import app.financecopilot.categorization.CategorizationResult.Assignment;
import app.financecopilot.domain.Category;
import app.financecopilot.domain.CategoryKind;
import app.financecopilot.domain.CategorySource;
import app.financecopilot.domain.Direction;
import app.financecopilot.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategorizationServiceTest {

    private static final long FOOD = 1L;
    private static final long TRANSPORT = 3L;
    private static final long UNCATEGORISED = 20L;

    private final CategoryRepository categories = mock(CategoryRepository.class);

    @BeforeEach
    void uncategorisedExists() {
        Category uncategorised = new Category(null, Category.UNCATEGORISED, CategoryKind.EXPENSE, "#ccc");
        ReflectionTestUtils.setField(uncategorised, "id", UNCATEGORISED);
        when(categories.findByUserIdIsNull()).thenReturn(List.of(uncategorised));
        when(categories.findByUserIdIsNullAndName(Category.UNCATEGORISED)).thenReturn(Optional.of(uncategorised));
    }

    /** Test step resolving fixed merchants and recording what it was asked about. */
    private static final class FakeStep implements CategorizationStep {
        private final CategorySource source;
        private final Map<String, Long> known;
        final List<List<String>> calls = new ArrayList<>();

        FakeStep(CategorySource source, Map<String, Long> known) {
            this.source = source;
            this.known = known;
        }

        @Override
        public CategorySource source() {
            return source;
        }

        @Override
        public Map<Integer, Long> resolve(Long userId, List<CategorizationInput> pending) {
            calls.add(pending.stream().map(CategorizationInput::merchant).toList());
            Map<Integer, Long> out = new HashMap<>();
            pending.forEach(p -> {
                if (known.containsKey(p.merchant())) {
                    out.put(p.index(), known.get(p.merchant()));
                }
            });
            return out;
        }
    }

    private static List<CategorizationInput> inputs(String... merchants) {
        List<CategorizationInput> list = new ArrayList<>();
        for (int i = 0; i < merchants.length; i++) {
            list.add(new CategorizationInput(i, merchants[i], merchants[i].toUpperCase(), Direction.DEBIT));
        }
        return list;
    }

    @Test
    void laterStepsOnlySeeWhatEarlierStepsCouldNotResolve() {
        FakeStep rules = new FakeStep(CategorySource.RULE, Map.of("Swiggy", FOOD));
        FakeStep cache = new FakeStep(CategorySource.CACHE, Map.of("Sharma Store", FOOD));
        FakeStep ai = new FakeStep(CategorySource.AI, Map.of("Rapido", TRANSPORT));
        CategorizationService service = new CategorizationService(List.of(rules, cache, ai), categories);

        CategorizationResult result = service.categorize(1L, inputs("Swiggy", "Sharma Store", "Rapido", "Mystery"));

        assertThat(cache.calls).containsExactly(List.of("Sharma Store", "Rapido", "Mystery"));
        assertThat(ai.calls).containsExactly(List.of("Rapido", "Mystery"));
        assertThat(result.assignments()).containsEntry(0, new Assignment(FOOD, CategorySource.RULE))
                .containsEntry(1, new Assignment(FOOD, CategorySource.CACHE))
                .containsEntry(2, new Assignment(TRANSPORT, CategorySource.AI))
                .containsEntry(3, new Assignment(UNCATEGORISED, CategorySource.NONE));
        assertThat(result.stats()).isEqualTo(new CategorizationResult.Stats(1, 1, 1, 1));
        assertThat(result.stats().withoutAiPercent()).isEqualTo(50.0);
    }

    @Test
    void aiIsNotCalledWhenRulesResolveEverything() {
        FakeStep rules = new FakeStep(CategorySource.RULE, Map.of("Uber", TRANSPORT));
        FakeStep ai = new FakeStep(CategorySource.AI, Map.of());
        CategorizationService service = new CategorizationService(List.of(rules, ai), categories);

        CategorizationResult result = service.categorize(1L, inputs("Uber", "Uber"));

        assertThat(ai.calls).isEmpty();
        assertThat(result.stats().withoutAiPercent()).isEqualTo(100.0);
    }

    @Test
    void failingStepFallsBackToUncategorisedInsteadOfFailing() {
        CategorizationStep broken = new CategorizationStep() {
            @Override
            public CategorySource source() {
                return CategorySource.AI;
            }

            @Override
            public Map<Integer, Long> resolve(Long userId, List<CategorizationInput> pending) {
                throw new IllegalStateException("Gemini down");
            }
        };
        CategorizationService service = new CategorizationService(List.of(broken), categories);

        CategorizationResult result = service.categorize(1L, inputs("Unknown Shop"));

        assertThat(result.assignments().get(0)).isEqualTo(new Assignment(UNCATEGORISED, CategorySource.NONE));
    }
}
