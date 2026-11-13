package app.financecopilot.repository;

import app.financecopilot.domain.Transaction;
import app.financecopilot.dto.TransactionDtos.TransactionFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /** The user_id predicate is always applied; filters only ever narrow the caller's own rows. */
    public static Specification<Transaction> forUser(Long userId, TransactionFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("date"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("date"), f.to()));
            }
            if (f.categoryId() != null) {
                p.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.direction() != null) {
                p.add(cb.equal(root.get("direction"), f.direction()));
            }
            if (f.source() != null) {
                p.add(cb.equal(root.get("categorySource"), f.source()));
            }
            if (f.minAmount() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("amount"), f.minAmount()));
            }
            if (f.maxAmount() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("amount"), f.maxAmount()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("merchant")), like, '\\'),
                        cb.like(cb.lower(root.get("descriptionRaw")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
