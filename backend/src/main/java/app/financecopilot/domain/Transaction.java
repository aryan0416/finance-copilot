package app.financecopilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Read/update model. Bulk inserts during import go through {@code TransactionBatchWriter} (JDBC batch,
 * one round trip) because IDENTITY ids defeat Hibernate insert batching.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "statement_id")
    private Long statementId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate date;

    @Column(name = "description_raw", nullable = false)
    private String descriptionRaw;

    @Column(name = "merchant_normalized", nullable = false)
    private String merchant;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    private BigDecimal balance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false)
    private CategorySource categorySource;

    @Column(nullable = false, columnDefinition = "bpchar(64)")
    private String hash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Transaction() {
    }

    public void recategorize(Category category, CategorySource source) {
        this.category = category;
        this.categorySource = source;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getStatementId() {
        return statementId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescriptionRaw() {
        return descriptionRaw;
    }

    public String getMerchant() {
        return merchant;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Category getCategory() {
        return category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }
}
