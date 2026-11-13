package app.financecopilot.repository;

import app.financecopilot.domain.CategorySource;
import app.financecopilot.domain.Direction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Bulk insert for statement imports. With {@code reWriteBatchedInserts} the driver sends one multi-row
 * INSERT per batch, which matters against a remote pooler where each round trip costs ~100 ms.
 * {@code ON CONFLICT DO NOTHING} makes concurrent uploads of overlapping statements safe.
 */
@Repository
public class TransactionBatchWriter {

    private static final String INSERT = """
            INSERT INTO transactions (user_id, statement_id, txn_date, description_raw, merchant_normalized,
                                      amount, direction, balance, category_id, category_source, hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, hash) DO NOTHING""";

    public record NewTransaction(Long userId, Long statementId, LocalDate date, String descriptionRaw,
                                 String merchant, BigDecimal amount, Direction direction, BigDecimal balance,
                                 Long categoryId, CategorySource categorySource, String hash) {
    }

    private final JdbcTemplate jdbc;

    public TransactionBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return number of rows actually inserted (conflicting hashes are skipped). */
    public int insertAll(List<NewTransaction> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        int[][] results = jdbc.batchUpdate(INSERT, rows, 500, (ps, t) -> {
            ps.setLong(1, t.userId());
            ps.setLong(2, t.statementId());
            ps.setDate(3, Date.valueOf(t.date()));
            ps.setString(4, t.descriptionRaw());
            ps.setString(5, t.merchant());
            ps.setBigDecimal(6, t.amount());
            ps.setString(7, t.direction().name());
            if (t.balance() == null) {
                ps.setNull(8, Types.NUMERIC);
            } else {
                ps.setBigDecimal(8, t.balance());
            }
            ps.setLong(9, t.categoryId());
            ps.setString(10, t.categorySource().name());
            ps.setString(11, t.hash());
        });
        // With rewritten batches the driver reports SUCCESS_NO_INFO (-2) per row; count those as inserted.
        return Arrays.stream(results).flatMapToInt(Arrays::stream).map(n -> n == -2 ? 1 : n).sum();
    }
}
