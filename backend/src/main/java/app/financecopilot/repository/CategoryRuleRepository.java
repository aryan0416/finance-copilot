package app.financecopilot.repository;

import app.financecopilot.domain.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    List<CategoryRule> findByUserIdOrderByPriorityAscIdAsc(Long userId);

    List<CategoryRule> findByUserIdIsNullOrderByPriorityAscIdAsc();

    boolean existsByUserIdAndPatternIgnoreCase(Long userId, String pattern);
}
