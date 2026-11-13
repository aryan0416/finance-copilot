package app.financecopilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Shared merchant → system category mapping learned from the LLM, so each merchant is classified once. */
@Entity
@Table(name = "merchant_category_cache")
public class MerchantCategoryCache {

    @Id
    @Column(name = "merchant_normalized")
    private String merchant;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MerchantCategoryCache() {
    }

    public MerchantCategoryCache(String merchant, Long categoryId) {
        this.merchant = merchant;
        this.categoryId = categoryId;
    }

    public String getMerchant() {
        return merchant;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public int getHitCount() {
        return hitCount;
    }
}
