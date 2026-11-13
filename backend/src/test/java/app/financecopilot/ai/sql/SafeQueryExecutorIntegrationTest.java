package app.financecopilot.ai.sql;

import app.financecopilot.ai.sql.SqlValidator.ValidatedQuery;
import app.financecopilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Defence in depth: these feed the executor SQL that the validator would never pass, to prove the
 * database-level guards hold on their own.
 */
class SafeQueryExecutorIntegrationTest extends IntegrationTest {

    @Autowired
    SafeQueryExecutor executor;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void writesAreRefusedByTheReadOnlyTransaction() {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);

        assertThatThrownBy(() -> executor.execute(1L, new ValidatedQuery("DELETE FROM users")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("read-only transaction");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(before);
    }

    @Test
    void slowQueriesAreCancelledByTheStatementTimeout() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> executor.execute(1L, new ValidatedQuery("SELECT pg_sleep(10)")))
                .isInstanceOf(DataAccessException.class);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(8_000);
    }

    @Test
    void resultSizeIsCappedEvenWithoutALimit() {
        var result = executor.execute(1L, new ValidatedQuery("SELECT g FROM generate_series(1, 5000) g"));
        assertThat(result.rows()).hasSize(SqlValidator.MAX_ROWS);
    }
}
