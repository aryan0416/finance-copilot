package app.financecopilot.ai.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Gatekeeper for LLM-generated SQL. The model is untrusted input, so the query is parsed into an AST
 * (JSqlParser) and accepted only if every rule holds; anything unparseable or unexpected is rejected
 * (fail closed):
 * <ol>
 *   <li>Exactly one statement, and it is a SELECT: no DML/DDL, no stacked {@code ; DELETE ...}.</li>
 *   <li>No SQL comments, which can hide payloads from naive checks.</li>
 *   <li>No WITH clause (the executor supplies the only CTE), no SELECT INTO, no FOR UPDATE/SHARE.</li>
 *   <li>Every table reference, in joins, subqueries and set operations alike, is the virtual
 *       {@code my_transactions} table, unqualified. So no {@code users}, {@code pg_catalog.*} or
 *       {@code information_schema}.</li>
 *   <li>Every function (including window functions) is on an allowlist; {@code pg_sleep},
 *       {@code pg_read_file}, {@code current_setting}, {@code dblink} and friends are rejected.</li>
 *   <li>A LIMIT of at most {@value #MAX_ROWS} is enforced (added if missing, clamped if larger).</li>
 * </ol>
 * The result is re-serialised from the AST, so what runs is exactly what was validated.
 */
@Component
public class SqlValidator {

    public static final String VIRTUAL_TABLE = "my_transactions";
    public static final int MAX_ROWS = 500;
    static final int MAX_SQL_LENGTH = 4000;

    static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            // aggregates
            "count", "sum", "avg", "min", "max", "stddev", "stddev_samp", "stddev_pop", "variance", "string_agg",
            "bool_or", "bool_and",
            // window
            "row_number", "rank", "dense_rank", "lag", "lead", "first_value", "last_value", "ntile",
            // numeric
            "round", "abs", "ceil", "ceiling", "floor", "greatest", "least",
            // null handling
            "coalesce", "nullif",
            // text
            "lower", "upper", "trim", "length", "concat", "substring", "substr", "replace", "split_part",
            "left", "right", "initcap", "position",
            // dates
            "date_trunc", "date_part", "extract", "to_char", "to_date", "make_date", "age", "now");

    public record ValidatedQuery(String sql) {
    }

    public ValidatedQuery validate(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new UnsafeSqlException("Empty query");
        }
        String sql = rawSql.strip();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).strip(); // a single trailing semicolon is harmless
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            throw new UnsafeSqlException("Query is too long");
        }
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new UnsafeSqlException("SQL comments are not allowed");
        }
        if (sql.contains(";")) {
            throw new UnsafeSqlException("Only a single statement is allowed");
        }

        Statement statement = parseSingle(sql);
        if (!(statement instanceof Select select)) {
            throw new UnsafeSqlException("Only SELECT statements are allowed");
        }
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            throw new UnsafeSqlException("WITH clauses are not allowed");
        }

        Inspector inspector = new Inspector();
        Set<String> tables;
        try {
            tables = inspector.getTables((Statement) select);
        } catch (UnsupportedOperationException e) {
            throw new UnsafeSqlException("Unsupported SQL construct");
        }
        if (inspector.forbiddenClause != null) {
            throw new UnsafeSqlException(inspector.forbiddenClause + " is not allowed");
        }
        for (String table : tables) {
            if (!VIRTUAL_TABLE.equals(table.toLowerCase(Locale.ROOT))) {
                throw new UnsafeSqlException("Access to table '" + table + "' is not allowed; only "
                        + VIRTUAL_TABLE + " can be queried");
            }
        }
        for (String function : inspector.functions) {
            if (!ALLOWED_FUNCTIONS.contains(function)) {
                throw new UnsafeSqlException("Function '" + function + "' is not allowed");
            }
        }

        enforceLimit(select);
        return new ValidatedQuery(select.toString());
    }

    private static Statement parseSingle(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.size() != 1) {
                throw new UnsafeSqlException("Only a single statement is allowed");
            }
            return statements.getFirst();
        } catch (JSQLParserException e) {
            throw new UnsafeSqlException("Could not parse the SQL");
        }
    }

    private static void enforceLimit(Select select) {
        Limit limit = select.getLimit();
        if (limit == null || !(limit.getRowCount() instanceof LongValue rowCount)
                || rowCount.getValue() > MAX_ROWS || rowCount.getValue() < 0) {
            select.setLimit(new Limit().withRowCount(new LongValue(MAX_ROWS)));
        }
    }

    /**
     * Walks the whole AST (TablesNamesFinder already descends into joins, subqueries, set operations and
     * expressions) while also recording every function name and disallowed clause.
     */
    private static final class Inspector extends TablesNamesFinder<Void> {

        final Set<String> functions = new HashSet<>();
        String forbiddenClause;

        @Override
        public <S> Void visit(Function function, S context) {
            functions.add(function.getName().toLowerCase(Locale.ROOT));
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(AnalyticExpression expression, S context) {
            functions.add(expression.getName().toLowerCase(Locale.ROOT));
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(PlainSelect plainSelect, S context) {
            if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()
                    || plainSelect.getIntoTempTable() != null) {
                forbiddenClause = "SELECT INTO";
            }
            if (plainSelect.getForMode() != null || plainSelect.getForUpdateTable() != null) {
                forbiddenClause = "FOR UPDATE/SHARE";
            }
            if (plainSelect.getWithItemsList() != null && !plainSelect.getWithItemsList().isEmpty()) {
                forbiddenClause = "WITH";
            }
            return super.visit(plainSelect, context);
        }
    }
}
