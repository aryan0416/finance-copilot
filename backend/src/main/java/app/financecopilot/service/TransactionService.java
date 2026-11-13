package app.financecopilot.service;

import app.financecopilot.domain.Category;
import app.financecopilot.domain.CategoryRule;
import app.financecopilot.domain.CategorySource;
import app.financecopilot.domain.MatchType;
import app.financecopilot.domain.Transaction;
import app.financecopilot.dto.TransactionDtos.PageResponse;
import app.financecopilot.dto.TransactionDtos.RecategorizeRequest;
import app.financecopilot.dto.TransactionDtos.RecategorizeResponse;
import app.financecopilot.dto.TransactionDtos.TransactionFilter;
import app.financecopilot.dto.TransactionDtos.TransactionResponse;
import app.financecopilot.exception.ApiException;
import app.financecopilot.repository.CategoryRuleRepository;
import app.financecopilot.repository.TransactionRepository;
import app.financecopilot.repository.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class TransactionService {

    /** User rules beat every built-in rule (built-ins start at 50). */
    static final int USER_RULE_PRIORITY = 10;

    private final TransactionRepository transactions;
    private final CategoryRuleRepository rules;
    private final CategoryService categoryService;

    public TransactionService(TransactionRepository transactions, CategoryRuleRepository rules,
                              CategoryService categoryService) {
        this.transactions = transactions;
        this.rules = rules;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Long userId, TransactionFilter filter, Pageable pageable) {
        Page<Transaction> page = transactions.findAll(TransactionSpecifications.forUser(userId, filter), pageable);
        return PageResponse.of(page, TransactionService::toResponse);
    }

    /**
     * Manual recategorisation (source = USER). With {@code createRule}, the merchant becomes a user rule so
     * future imports match it in step (a), and the user's other transactions from that merchant that were
     * not manually edited are moved to the new category as well.
     */
    @Transactional
    public RecategorizeResponse recategorize(Long userId, Long transactionId, RecategorizeRequest request) {
        Transaction txn = transactions.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction")); // other users' ids look like 404
        Category category = categoryService.requireVisible(userId, request.categoryId());
        txn.recategorize(category, CategorySource.USER);

        boolean ruleCreated = false;
        int similarUpdated = 0;
        if (request.createRule()) {
            String pattern = txn.getMerchant().toLowerCase(Locale.ROOT);
            if (!rules.existsByUserIdAndPatternIgnoreCase(userId, pattern)) {
                rules.save(new CategoryRule(userId, pattern, MatchType.KEYWORD, category.getId(), USER_RULE_PRIORITY));
                ruleCreated = true;
            }
            similarUpdated = transactions.applyCategoryToMerchant(userId, txn.getMerchant(), category.getId(),
                    txn.getId());
        }
        return new RecategorizeResponse(toResponse(txn), ruleCreated, similarUpdated);
    }

    static TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.getId(), t.getDate(), t.getDescriptionRaw(), t.getMerchant(), t.getAmount(),
                t.getDirection(), t.getBalance(), CategoryService.toResponse(t.getCategory()), t.getCategorySource());
    }
}
