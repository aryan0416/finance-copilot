package app.financecopilot.categorization;

import app.financecopilot.domain.CategorySource;
import app.financecopilot.domain.MerchantCategoryCache;
import app.financecopilot.repository.MerchantCategoryCacheRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * (c) Shared merchant → category cache. Every merchant the LLM has classified for any user is answered
 * here from then on, so the AI cost of a merchant is paid once across the whole user base.
 */
@Component
@Order(30)
class MerchantCacheStep implements CategorizationStep {

    private final MerchantCategoryCacheRepository cache;

    MerchantCacheStep(MerchantCategoryCacheRepository cache) {
        this.cache = cache;
    }

    @Override
    public CategorySource source() {
        return CategorySource.CACHE;
    }

    @Override
    public Map<Integer, Long> resolve(Long userId, List<CategorizationInput> pending) {
        Set<String> merchants = pending.stream().map(CategorizationInput::merchant).collect(Collectors.toSet());
        Map<String, Long> hits = cache.findByMerchantIn(merchants).stream()
                .collect(Collectors.toMap(MerchantCategoryCache::getMerchant, MerchantCategoryCache::getCategoryId));
        if (hits.isEmpty()) {
            return Map.of();
        }
        cache.incrementHits(hits.keySet());
        Map<Integer, Long> resolved = new HashMap<>();
        for (CategorizationInput input : pending) {
            Long categoryId = hits.get(input.merchant());
            if (categoryId != null) {
                resolved.put(input.index(), categoryId);
            }
        }
        return resolved;
    }
}
