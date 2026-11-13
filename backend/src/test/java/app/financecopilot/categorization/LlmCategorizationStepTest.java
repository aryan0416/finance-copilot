package app.financecopilot.categorization;

import app.financecopilot.ai.LlmClient;
import app.financecopilot.ai.LlmClient.LlmResponse;
import app.financecopilot.ai.LlmUnavailableException;
import app.financecopilot.domain.AiQueryLog;
import app.financecopilot.domain.Category;
import app.financecopilot.domain.CategoryKind;
import app.financecopilot.domain.Direction;
import app.financecopilot.domain.MerchantCategoryCache;
import app.financecopilot.repository.AiQueryLogRepository;
import app.financecopilot.repository.CategoryRepository;
import app.financecopilot.repository.MerchantCategoryCacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmCategorizationStepTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final MerchantCategoryCacheRepository cache = mock(MerchantCategoryCacheRepository.class);
    private final AiQueryLogRepository aiLog = mock(AiQueryLogRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void systemCategories() {
        when(categories.findByUserIdIsNull()).thenReturn(List.of(
                category(1L, "Food & Dining"), category(2L, "Groceries"), category(20L, Category.UNCATEGORISED)));
    }

    private static Category category(long id, String name) {
        Category c = new Category(null, name, CategoryKind.EXPENSE, "#000");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static List<CategorizationInput> inputs(String... merchants) {
        List<CategorizationInput> list = new ArrayList<>();
        for (int i = 0; i < merchants.length; i++) {
            list.add(new CategorizationInput(i, merchants[i], "UPI/123456789012/" + merchants[i], Direction.DEBIT));
        }
        return list;
    }

    private LlmCategorizationStep step(LlmClient llm, int batchSize) {
        return new LlmCategorizationStep(llm, categories, cache, aiLog, mapper, batchSize);
    }

    @Test
    void acceptsValidAnswersCachesThemAndDropsInventedCategories() {
        LlmClient llm = request -> new LlmResponse("""
                ```json
                {"results":[{"id":1,"category":"Groceries"},{"id":2,"category":"Pet Care"},{"id":3,"category":"food & dining"},{"id":99,"category":"Groceries"}]}
                ```""", 100, 20, 300);

        Map<Integer, Long> resolved = step(llm, 50)
                .resolve(7L, inputs("Sharma General Store", "Pawsome", "Sri Krishna Tiffin"));

        assertThat(resolved).containsOnly(Map.entry(0, 2L), Map.entry(2, 1L)); // "Pet Care" is not allowed
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MerchantCategoryCache>> cached = ArgumentCaptor.forClass(List.class);
        verify(cache).saveAll(cached.capture());
        assertThat(cached.getValue()).extracting(MerchantCategoryCache::getMerchant)
                .containsExactlyInAnyOrder("Sharma General Store", "Sri Krishna Tiffin");
        verify(aiLog).save(any(AiQueryLog.class));
    }

    @Test
    void sendsEachMerchantOnceAndBatchesByConfiguredSize() {
        AtomicInteger calls = new AtomicInteger();
        List<String> prompts = new ArrayList<>();
        LlmClient llm = request -> {
            calls.incrementAndGet();
            prompts.add(request.userPrompt());
            return new LlmResponse("{\"results\":[]}", null, null, 1);
        };

        // 5 distinct merchants, one of them repeated 3 times
        step(llm, 2).resolve(1L, inputs("A Shop", "B Shop", "A Shop", "C Shop", "A Shop", "D Shop", "E Shop"));

        assertThat(calls).hasValue(3); // ceil(5 / 2)
        assertThat(String.join("", prompts).split("\"merchant\":\"A Shop\"", -1)).hasSize(2); // appears once
        verify(aiLog, times(3)).save(any(AiQueryLog.class));
    }

    @Test
    void doesNotLeakReferenceNumbersToTheModel() {
        List<String> prompts = new ArrayList<>();
        LlmClient llm = request -> {
            prompts.add(request.userPrompt());
            return new LlmResponse("{\"results\":[]}", null, null, 1);
        };

        step(llm, 50).resolve(1L, inputs("Corner Shop"));

        assertThat(prompts.getFirst()).doesNotContain("123456789012").contains("UPI/#/Corner Shop");
    }

    @Test
    void failedBatchResolvesNothingAndIsLogged() {
        LlmClient down = request -> {
            throw new LlmUnavailableException("LLM unavailable after retries: 429");
        };

        Map<Integer, Long> resolved = step(down, 50).resolve(1L, inputs("Corner Shop"));

        assertThat(resolved).isEmpty();
        verify(cache, never()).saveAll(any());
        ArgumentCaptor<AiQueryLog> log = ArgumentCaptor.forClass(AiQueryLog.class);
        verify(aiLog).save(log.capture());
        assertThat(log.getValue().getValid()).isFalse();
    }

    @Test
    void uncategorisedAnswersAreNotCached() {
        LlmClient llm = request -> new LlmResponse("{\"results\":[{\"id\":1,\"category\":\"Uncategorised\"}]}", null, null, 1);

        assertThat(step(llm, 50).resolve(1L, inputs("Xyz"))).isEmpty();
        verify(cache, never()).saveAll(any());
    }
}
