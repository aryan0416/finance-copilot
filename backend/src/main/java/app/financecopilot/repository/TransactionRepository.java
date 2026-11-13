package app.financecopilot.repository;

import app.financecopilot.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** Fetches the category in the same query to avoid N+1 lazy loads on list pages. */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    @Query(value = "SELECT hash FROM transactions WHERE user_id = :userId AND hash IN (:hashes)", nativeQuery = true)
    List<String> findExistingHashes(Long userId, Collection<String> hashes);

    /** Moves the user's other transactions of a merchant to a category, leaving manual (USER) edits alone. */
    @Modifying
    @Query(value = """
            UPDATE transactions SET category_id = :categoryId, category_source = 'RULE'
            WHERE user_id = :userId AND merchant_normalized = :merchant AND id <> :excludeId
              AND category_source <> 'USER'""", nativeQuery = true)
    int applyCategoryToMerchant(Long userId, String merchant, Long categoryId, Long excludeId);

    long countByUserId(Long userId);
}
