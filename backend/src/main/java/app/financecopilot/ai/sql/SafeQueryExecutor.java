package app.financecopilot.ai.sql;

import app.financecopilot.ai.sql.SqlValidator.ValidatedQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a validated query against the caller's data only. Defence in depth on top of {@link SqlValidator}:
 * <ul>
 *   <li>Row-level scoping: the query can only see {@code my_transactions}, a CTE over the caller's rows,
 *       with the user id bound as a JDBC parameter (never concatenated).</li>
 *   <li>READ ONLY transaction: even a write that slipped past validation is refused by Postgres.</li>
 *   <li>{@code SET LOCAL statement_timeout = '3s'}: no runaway or deliberately slow queries.</li>
 *   <li>At most {@link SqlValidator#MAX_ROWS} rows are read, whatever the query says.</li>
 * </ul>
 */
@Component
public class SafeQueryExecutor {

    /** The only data an Ask-AI query can reach. The column list is the schema the LLM is shown. */
    static final String SCOPED_CTE = """
            WITH my_transactions AS (
                SELECT t.txn_date AS date, t.merchant_normalized AS merchant, t.description_raw AS description,
                       t.amount, t.direction, COALESCE(c.name, 'Uncategorised') AS category
                FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ?
            )
            """;

    public record QueryResult(List<String> columns, List<List<Object>> rows) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate readOnlyTx;

    public SafeQueryExecutor(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public QueryResult execute(Long userId, ValidatedQuery query) {
        return readOnlyTx.execute(status -> {
            jdbc.execute("SET TRANSACTION READ ONLY");
            jdbc.execute("SET LOCAL statement_timeout = '3s'");
            return jdbc.query(SCOPED_CTE + query.sql(), ps -> {
                ps.setLong(1, userId);
                ps.setMaxRows(SqlValidator.MAX_ROWS);
            }, rs -> {
                ResultSetMetaData meta = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next() && rows.size() < SqlValidator.MAX_ROWS) {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) {
                        row.add(jsonSafe(rs.getObject(i)));
                    }
                    rows.add(row);
                }
                return new QueryResult(columns, rows);
            });
        });
    }

    /** Dates as ISO strings; numbers and strings pass through; anything exotic becomes its string form. */
    private static Object jsonSafe(Object value) {
        if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        return value.toString();
    }
}
