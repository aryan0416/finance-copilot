package app.financecopilot.repository;

import app.financecopilot.domain.MerchantCategoryCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface MerchantCategoryCacheRepository extends JpaRepository<MerchantCategoryCache, String> {

    List<MerchantCategoryCache> findByMerchantIn(Collection<String> merchants);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE merchant_category_cache SET hit_count = hit_count + 1, updated_at = now()
            WHERE merchant_normalized IN (:merchants)""", nativeQuery = true)
    void incrementHits(Collection<String> merchants);
}
