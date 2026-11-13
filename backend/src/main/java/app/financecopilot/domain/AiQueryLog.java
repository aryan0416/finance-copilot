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

/** Audit row for every LLM call (Ask-AI, categorisation batch, insight). */
@Entity
@Table(name = "ai_query_log")
public class AiQueryLog {

    public enum Kind { ASK, CATEGORIZE, INSIGHT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    private String question;

    @Column(name = "generated_sql")
    private String generatedSql;

    @Column(name = "is_valid")
    private Boolean valid;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AiQueryLog() {
    }

    public AiQueryLog(Long userId, Kind kind, String question) {
        this.userId = userId;
        this.kind = kind;
        this.question = question;
    }

    public AiQueryLog generatedSql(String sql) {
        this.generatedSql = sql;
        return this;
    }

    public AiQueryLog outcome(boolean valid, String rejectionReason, Integer rowCount) {
        this.valid = valid;
        this.rejectionReason = rejectionReason;
        this.rowCount = rowCount;
        return this;
    }

    public AiQueryLog usage(long latencyMs, Integer promptTokens, Integer completionTokens) {
        this.latencyMs = (int) Math.min(Integer.MAX_VALUE, latencyMs);
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        return this;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public Boolean getValid() {
        return valid;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getGeneratedSql() {
        return generatedSql;
    }
}
