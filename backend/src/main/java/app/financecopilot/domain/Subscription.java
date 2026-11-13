package app.financecopilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "merchant_normalized", nullable = false)
    private String merchant;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "avg_amount", nullable = false)
    private BigDecimal averageAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(nullable = false)
    private int occurrences;

    @Column(name = "last_charge_date", nullable = false)
    private LocalDate lastChargeDate;

    @Column(name = "next_expected_date", nullable = false)
    private LocalDate nextExpectedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Subscription() {
    }

    public Subscription(Long userId, String merchant, Long categoryId, BigDecimal averageAmount, Frequency frequency,
                        int occurrences, LocalDate lastChargeDate, LocalDate nextExpectedDate,
                        SubscriptionStatus status) {
        this.userId = userId;
        this.merchant = merchant;
        this.categoryId = categoryId;
        this.averageAmount = averageAmount;
        this.frequency = frequency;
        this.occurrences = occurrences;
        this.lastChargeDate = lastChargeDate;
        this.nextExpectedDate = nextExpectedDate;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getMerchant() {
        return merchant;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public BigDecimal getAverageAmount() {
        return averageAmount;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public LocalDate getLastChargeDate() {
        return lastChargeDate;
    }

    public LocalDate getNextExpectedDate() {
        return nextExpectedDate;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }
}
