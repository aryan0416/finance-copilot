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

/** A system category ({@code userId == null}) or a user-defined one. */
@Entity
@Table(name = "categories")
public class Category {

    public static final String UNCATEGORISED = "Uncategorised";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryKind kind = CategoryKind.EXPENSE;

    @Column(nullable = false)
    private String color = "#94a3b8";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Category() {
    }

    public Category(Long userId, String name, CategoryKind kind, String color) {
        this.userId = userId;
        this.name = name;
        this.kind = kind;
        this.color = color;
    }

    public boolean isSystem() {
        return userId == null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public CategoryKind getKind() {
        return kind;
    }

    public String getColor() {
        return color;
    }
}
