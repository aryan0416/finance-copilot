package app.financecopilot.ai.sql;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    private String valid(String sql) {
        return validator.validate(sql).sql();
    }

    private void rejected(String sql, String reasonFragment) {
        assertThatThrownBy(() -> validator.validate(sql))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining(reasonFragment);
    }

    @Nested
    class AcceptsAnalyticalQueries {

        @Test
        void aggregationWithFiltersAndGrouping() {
            String sql = valid("""
                    SELECT category, SUM(amount) AS total FROM my_transactions
                    WHERE direction = 'DEBIT' AND date >= DATE '2026-08-01'
                    GROUP BY category ORDER BY total DESC LIMIT 5""");
            assertThat(sql).contains("LIMIT 5").containsIgnoringCase("from my_transactions");
        }

        @Test
        void monthlyTrendWithDateFunctions() {
            assertThat(valid("""
                    SELECT to_char(date_trunc('month', date), 'YYYY-MM') AS month, SUM(amount) AS spent
                    FROM my_transactions WHERE merchant ILIKE '%swiggy%' AND direction = 'DEBIT'
                    GROUP BY 1 ORDER BY 1""")).contains("LIMIT 500");
        }

        @Test
        void subqueriesAndSetOperationsOverTheVirtualTable() {
            valid("""
                    SELECT merchant, amount FROM my_transactions
                    WHERE amount > (SELECT AVG(amount) * 3 FROM my_transactions WHERE direction = 'DEBIT')""");
            valid("SELECT 'in' AS kind, SUM(amount) FROM my_transactions WHERE direction = 'CREDIT' "
                    + "UNION ALL SELECT 'out', SUM(amount) FROM my_transactions WHERE direction = 'DEBIT'");
        }

        @Test
        void windowFunctions() {
            valid("SELECT merchant, amount, RANK() OVER (ORDER BY amount DESC) AS r FROM my_transactions");
        }

        @Test
        void trailingSemicolonIsTolerated() {
            assertThat(valid("SELECT COUNT(*) FROM my_transactions;")).doesNotContain(";");
        }

        @Test
        void addsLimitWhenMissingAndClampsOversizedLimits() {
            assertThat(valid("SELECT * FROM my_transactions")).endsWith("LIMIT 500");
            assertThat(valid("SELECT * FROM my_transactions LIMIT 100000")).endsWith("LIMIT 500");
            assertThat(valid("SELECT * FROM my_transactions LIMIT 20")).endsWith("LIMIT 20");
        }
    }

    @Nested
    class RejectsInjectionAttempts {

        @ParameterizedTest
        @ValueSource(strings = {
                "DROP TABLE transactions",
                "DELETE FROM my_transactions",
                "UPDATE my_transactions SET amount = 0",
                "INSERT INTO my_transactions (amount) VALUES (1)",
                "TRUNCATE transactions",
                "ALTER TABLE users ADD COLUMN x int",
                "GRANT ALL ON users TO public",
        })
        void nonSelectStatements(String sql) {
            assertThatThrownBy(() -> validator.validate(sql)).isInstanceOf(UnsafeSqlException.class);
        }

        @Test
        void stackedStatements() {
            rejected("SELECT * FROM my_transactions; DELETE FROM transactions", "single statement");
            rejected("SELECT 1; DROP TABLE users;", "single statement");
        }

        @Test
        void commentTricks() {
            rejected("SELECT * FROM my_transactions -- WHERE 1=0", "comments");
            rejected("SELECT * FROM my_transactions /* ; DROP TABLE users */", "comments");
        }

        @Test
        void otherTablesDirectlyOrViaJoinsAndSubqueries() {
            rejected("SELECT * FROM users", "users");
            rejected("SELECT * FROM transactions", "transactions");
            rejected("SELECT t.* FROM my_transactions t JOIN users u ON true", "users");
            rejected("SELECT * FROM my_transactions WHERE amount IN (SELECT id FROM users)", "users");
            rejected("SELECT (SELECT password_hash FROM users LIMIT 1) FROM my_transactions", "users");
            rejected("SELECT * FROM public.my_transactions", "public.my_transactions");
        }

        @Test
        void unionIntoOtherTables() {
            rejected("SELECT merchant, amount FROM my_transactions UNION SELECT email, 1 FROM users", "users");
            rejected("SELECT description FROM my_transactions UNION ALL SELECT password_hash FROM users", "users");
        }

        @Test
        void systemCatalogs() {
            rejected("SELECT * FROM pg_catalog.pg_user", "pg_catalog.pg_user");
            rejected("SELECT table_name FROM information_schema.tables", "information_schema.tables");
            rejected("SELECT * FROM pg_shadow", "pg_shadow");
        }

        @Test
        void dangerousFunctions() {
            rejected("SELECT pg_sleep(10) FROM my_transactions", "pg_sleep");
            rejected("SELECT pg_read_file('/etc/passwd')", "pg_read_file");
            rejected("SELECT current_setting('server_version')", "current_setting");
            rejected("SELECT * FROM my_transactions WHERE amount > (SELECT pg_sleep(5))", "pg_sleep");
            rejected("SELECT * FROM pg_ls_dir('.')", "pg_ls_dir");
            rejected("SELECT version()", "version");
            rejected("SELECT set_config('statement_timeout', '0', false)", "set_config");
            rejected("SELECT pg_catalog.sum(amount) FROM my_transactions", "pg_catalog.sum");
        }

        @Test
        void writesHiddenInsideSelect() {
            rejected("SELECT * INTO stolen FROM my_transactions", "SELECT INTO");
            rejected("SELECT * FROM my_transactions FOR UPDATE", "FOR UPDATE");
        }

        @Test
        void userSuppliedCtesCannotShadowTheVirtualTable() {
            rejected("WITH my_transactions AS (SELECT * FROM users) SELECT * FROM my_transactions", "WITH");
        }

        @Test
        void garbageAndEmptyInput() {
            rejected("", "Empty");
            rejected("how much did i spend on swiggy?", "parse");
            rejected("SELECT * FROM my_transactions WHERE " + "a=1 AND ".repeat(600) + "1=1", "too long");
        }
    }
}
