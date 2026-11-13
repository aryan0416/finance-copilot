package app.financecopilot.repository;

import app.financecopilot.domain.AiQueryLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQueryLogRepository extends JpaRepository<AiQueryLog, Long> {

    long countByUserIdAndKind(Long userId, AiQueryLog.Kind kind);
}
