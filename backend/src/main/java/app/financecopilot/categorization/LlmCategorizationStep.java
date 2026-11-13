package app.financecopilot.categorization;

import app.financecopilot.ai.LlmClient;
import app.financecopilot.ai.LlmClient.LlmRequest;
import app.financecopilot.ai.LlmClient.LlmResponse;
import app.financecopilot.ai.LlmJson;
import app.financecopilot.ai.LlmUnavailableException;
import app.financecopilot.domain.AiQueryLog;
import app.financecopilot.domain.Category;
import app.financecopilot.domain.CategorySource;
import app.financecopilot.domain.MerchantCategoryCache;
import app.financecopilot.repository.AiQueryLogRepository;
import app.financecopilot.repository.CategoryRepository;
import app.financecopilot.repository.MerchantCategoryCacheRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * (d) Last resort: classify the still-unknown merchants with the LLM.
 * <ul>
 *   <li>Deduplicated: each distinct merchant is sent once, however many transactions it has.</li>
 *   <li>Batched: up to {@code batchSize} (default 50) merchants per request.</li>
 *   <li>Strict JSON: the answer must use the given ids and an exact category name from the allowed list;
 *       anything else is discarded rather than trusted.</li>
 *   <li>Cached: accepted answers go into the shared merchant cache, so step (c) answers next time.</li>
 *   <li>Fail-soft: a failed batch is logged and skipped; its rows end up "Uncategorised".</li>
 * </ul>
 * Only the normalised merchant, the direction and a digit-masked narration are sent, never amounts,
 * balances or account details.
 */
@Component
@Order(40)
class LlmCategorizationStep implements CategorizationStep {

    private static final Logger log = LoggerFactory.getLogger(LlmCategorizationStep.class);

    private static final String SYSTEM_PROMPT = """
            You categorise merchants from Indian bank statements (UPI, card, NEFT narrations).
            Allowed categories (use the exact spelling): %s.
            Guidance: person names receiving or sending money -> Transfers; kirana / general / provision
            stores -> Groceries; tiffin, cafes, coffee, bakeries, restaurants -> Food & Dining; chemists and
            hospitals -> Health; salons -> Personal Care; petrol pumps / fuel stations -> Fuel; jewellers ->
            Shopping. If you genuinely cannot tell, use Uncategorised.
            Respond with ONLY a JSON object, no prose, in exactly this shape:
            {"results":[{"id":<input id>,"category":"<allowed category>"}]}""";

    private final LlmClient llm;
    private final CategoryRepository categories;
    private final MerchantCategoryCacheRepository cache;
    private final AiQueryLogRepository aiLog;
    private final ObjectMapper mapper;
    private final int batchSize;

    LlmCategorizationStep(LlmClient llm, CategoryRepository categories, MerchantCategoryCacheRepository cache,
                          AiQueryLogRepository aiLog, ObjectMapper mapper,
                          @Value("${app.ai.categorization.batch-size:50}") int batchSize) {
        this.llm = llm;
        this.categories = categories;
        this.cache = cache;
        this.aiLog = aiLog;
        this.mapper = mapper;
        this.batchSize = batchSize;
    }

    @Override
    public CategorySource source() {
        return CategorySource.AI;
    }

    @Override
    public Map<Integer, Long> resolve(Long userId, List<CategorizationInput> pending) {
        // One representative input per merchant.
        Map<String, CategorizationInput> byMerchant = new LinkedHashMap<>();
        pending.forEach(input -> byMerchant.putIfAbsent(input.merchant(), input));
        List<CategorizationInput> merchants = new ArrayList<>(byMerchant.values());

        Map<String, Long> categoryIdsByName = categories.findByUserIdIsNull().stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), Category::getId));
        String allowed = categories.findByUserIdIsNull().stream().map(Category::getName)
                .collect(Collectors.joining(", "));

        Map<String, Long> learned = new HashMap<>();
        for (int from = 0; from < merchants.size(); from += batchSize) {
            List<CategorizationInput> batch = merchants.subList(from, Math.min(merchants.size(), from + batchSize));
            learned.putAll(classifyBatch(userId, batch, allowed, categoryIdsByName));
        }

        Long uncategorisedId = categoryIdsByName.get(Category.UNCATEGORISED.toLowerCase(Locale.ROOT));
        List<MerchantCategoryCache> toCache = learned.entrySet().stream()
                .filter(e -> !e.getValue().equals(uncategorisedId))
                .map(e -> new MerchantCategoryCache(e.getKey(), e.getValue()))
                .toList();
        if (!toCache.isEmpty()) {
            cache.saveAll(toCache);
        }

        Map<Integer, Long> resolved = new HashMap<>();
        for (CategorizationInput input : pending) {
            Long categoryId = learned.get(input.merchant());
            if (categoryId != null && !categoryId.equals(uncategorisedId)) {
                resolved.put(input.index(), categoryId);
            }
        }
        return resolved;
    }

    /** @return merchant → category id for every answer that passed validation. */
    private Map<String, Long> classifyBatch(Long userId, List<CategorizationInput> batch, String allowed,
                                            Map<String, Long> categoryIdsByName) {
        AiQueryLog entry = new AiQueryLog(userId, AiQueryLog.Kind.CATEGORIZE, "Categorise " + batch.size() + " merchants");
        try {
            LlmResponse response = llm.complete(new LlmRequest(SYSTEM_PROMPT.formatted(allowed), userPrompt(batch)));
            entry.usage(response.latencyMs(), response.promptTokens(), response.completionTokens());
            Map<String, Long> accepted = parseAnswer(response.text(), batch, categoryIdsByName);
            entry.outcome(true, accepted.size() < batch.size()
                    ? (batch.size() - accepted.size()) + " answers missing or invalid" : null, accepted.size());
            return accepted;
        } catch (LlmUnavailableException e) {
            log.warn("AI categorisation batch of {} skipped: {}", batch.size(), e.getMessage());
            entry.outcome(false, e.getMessage(), 0);
            return Map.of();
        } finally {
            aiLog.save(entry);
        }
    }

    private String userPrompt(List<CategorizationInput> batch) {
        ArrayNode items = mapper.createArrayNode();
        for (int i = 0; i < batch.size(); i++) {
            CategorizationInput input = batch.get(i);
            items.addObject()
                    .put("id", i + 1)
                    .put("merchant", input.merchant())
                    .put("direction", input.direction().name())
                    .put("narration", maskDigits(input.description()));
        }
        return "Categorise these merchants:\n" + items;
    }

    Map<String, Long> parseAnswer(String text, List<CategorizationInput> batch, Map<String, Long> categoryIdsByName) {
        JsonNode results = LlmJson.parseObject(mapper, text).path("results");
        if (!results.isArray()) {
            throw new LlmUnavailableException("Model JSON had no results array");
        }
        Map<Integer, CategorizationInput> byId = new HashMap<>();
        for (int i = 0; i < batch.size(); i++) {
            byId.put(i + 1, batch.get(i));
        }
        Map<String, Long> accepted = new HashMap<>();
        for (JsonNode result : results) {
            CategorizationInput input = byId.get(result.path("id").asInt(-1));
            Long categoryId = categoryIdsByName.get(result.path("category").asText("").trim().toLowerCase(Locale.ROOT));
            if (input != null && categoryId != null) { // unknown ids / invented categories are dropped
                accepted.putIfAbsent(input.merchant(), categoryId);
            }
        }
        return accepted;
    }

    /** UPI reference numbers and phone numbers are noise for classification and shouldn't leave the system. */
    private static String maskDigits(String narration) {
        String masked = narration.replaceAll("\\d{4,}", "#");
        return masked.length() > 140 ? masked.substring(0, 140) : masked;
    }
}
