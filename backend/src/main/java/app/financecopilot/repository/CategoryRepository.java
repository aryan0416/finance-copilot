package app.financecopilot.repository;

import app.financecopilot.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** System categories plus the user's own. */
    @Query("select c from Category c where c.userId is null or c.userId = :userId order by c.name")
    List<Category> findVisibleTo(Long userId);

    @Query("select c from Category c where c.id = :id and (c.userId is null or c.userId = :userId)")
    Optional<Category> findVisibleById(Long id, Long userId);

    List<Category> findByUserIdIsNull();

    Optional<Category> findByUserIdIsNullAndName(String name);
}
