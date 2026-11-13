package app.financecopilot.service;

import app.financecopilot.domain.Frequency;
import app.financecopilot.domain.Subscription;
import app.financecopilot.domain.SubscriptionStatus;
import app.financecopilot.dto.SubscriptionDtos.SubscriptionResponse;
import app.financecopilot.dto.TransactionDtos.CategoryResponse;
import app.financecopilot.repository.CategoryRepository;
import app.financecopilot.repository.SubscriptionRepository;
import app.financecopilot.service.StatementImportService.StatementImported;
import app.financecopilot.subscription.SubscriptionDetector;
import app.financecopilot.subscription.SubscriptionDetector.Charge;
import app.financecopilot.subscription.SubscriptionDetector.DetectedSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Re-derives a user's subscriptions from their full debit history after every import. */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final JdbcTemplate jdbc;
    private final SubscriptionRepository subscriptions;
    private final CategoryRepository categories;
    private final TransactionTemplate transactionTemplate;
    private final SubscriptionDetector detector = new SubscriptionDetector();

    public SubscriptionService(JdbcTemplate jdbc, SubscriptionRepository subscriptions, CategoryRepository categories,
                               TransactionTemplate transactionTemplate) {
        this.jdbc = jdbc;
        this.subscriptions = subscriptions;
        this.categories = categories;
        this.transactionTemplate = transactionTemplate;
    }

    /** Subscriptions are derived data: a failure here is logged, never surfaced as a failed upload. */
    @EventListener
    public void onStatementImported(StatementImported event) {
        try {
            refresh(event.userId());
        } catch (RuntimeException e) {
            log.error("Subscription refresh failed for user {}", event.userId(), e);
        }
    }

    public List<SubscriptionResponse> refresh(Long userId) {
        transactionTemplate.executeWithoutResult(status -> redetect(userId));
        return list(userId);
    }

    private void redetect(Long userId) {
        Map<String, Map<Long, Integer>> categoryVotes = new HashMap<>();
        List<Charge> debits = jdbc.query("""
                        SELECT merchant_normalized, txn_date, amount, category_id FROM transactions
                        WHERE user_id = ? AND direction = 'DEBIT'""",
                (rs, n) -> {
                    String merchant = rs.getString(1);
                    long categoryId = rs.getLong(4);
                    if (!rs.wasNull()) {
                        categoryVotes.computeIfAbsent(merchant, m -> new HashMap<>()).merge(categoryId, 1, Integer::sum);
                    }
                    return new Charge(merchant, rs.getDate(2).toLocalDate(), rs.getBigDecimal(3));
                }, userId);

        LocalDate reference = jdbc.queryForObject(
                "SELECT max(txn_date) FROM transactions WHERE user_id = ?", LocalDate.class, userId);
        if (reference == null) {
            subscriptions.deleteAllForUser(userId);
            return;
        }

        List<DetectedSubscription> detected = detector.detect(debits, reference);
        subscriptions.deleteAllForUser(userId);
        subscriptions.saveAll(detected.stream().map(d -> new Subscription(userId, d.merchant(),
                mostCommon(categoryVotes.get(d.merchant())), d.averageAmount(), d.frequency(), d.occurrences(),
                d.lastChargeDate(), d.nextExpectedDate(),
                d.active() ? SubscriptionStatus.ACTIVE : SubscriptionStatus.INACTIVE)).toList());
        log.debug("User {}: {} subscriptions detected from {} debits", userId, detected.size(), debits.size());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(Long userId) {
        Map<Long, CategoryResponse> categoryById = categories.findVisibleTo(userId).stream()
                .map(CategoryService::toResponse)
                .collect(Collectors.toMap(CategoryResponse::id, Function.identity()));
        return subscriptions.findByUserIdOrderByNextExpectedDateAsc(userId).stream()
                .map(s -> new SubscriptionResponse(s.getId(), s.getMerchant(), categoryById.get(s.getCategoryId()),
                        s.getAverageAmount(), s.getFrequency(), s.getOccurrences(), s.getLastChargeDate(),
                        s.getNextExpectedDate(), s.getStatus(), yearlyCost(s.getAverageAmount(), s.getFrequency())))
                .toList();
    }

    static BigDecimal yearlyCost(BigDecimal amount, Frequency frequency) {
        int perYear = switch (frequency) {
            case WEEKLY -> 52;
            case MONTHLY -> 12;
            case YEARLY -> 1;
        };
        return amount.multiply(BigDecimal.valueOf(perYear));
    }

    private static Long mostCommon(Map<Long, Integer> votes) {
        if (votes == null || votes.isEmpty()) {
            return null;
        }
        return votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
}
