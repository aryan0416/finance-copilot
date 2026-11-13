package app.financecopilot.repository;

import app.financecopilot.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserIdOrderByNextExpectedDateAsc(Long userId);

    @Modifying
    @Query("delete from Subscription s where s.userId = :userId")
    void deleteAllForUser(Long userId);
}
