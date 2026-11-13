package app.financecopilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Keyword/regex → category. {@code userId == null} marks a built-in rule for well-known merchants. */
@Entity
@Table(name = "category_rules")
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private MatchType matchType;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CategoryRule() {
    }

    public CategoryRule(Long userId, String pattern, MatchType matchType, Long categoryId, int priority) {
        this.userId = userId;
        this.pattern = pattern;
        this.matchType = matchType;
        this.categoryId = categoryId;
        this.priority = priority;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPattern() {
        return pattern;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public int getPriority() {
        return priority;
    }
}
