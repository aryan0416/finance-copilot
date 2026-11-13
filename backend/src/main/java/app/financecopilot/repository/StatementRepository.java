package app.financecopilot.repository;

import app.financecopilot.domain.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    boolean existsByUserIdAndFileHash(Long userId, String fileHash);

    List<Statement> findByUserIdOrderByUploadedAtDesc(Long userId);
}
