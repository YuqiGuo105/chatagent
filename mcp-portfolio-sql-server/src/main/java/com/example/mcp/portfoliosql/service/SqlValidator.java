package com.example.mcp.portfoliosql.service;

import com.example.mcp.portfoliosql.config.PortfolioSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates LLM-generated SQL before execution.
 *
 * Rules:
 *  1. Must parse as a single statement.
 *  2. Must be a SELECT (no DDL / DML / TCL).
 *  3. Every referenced table must be in the configured allow-list of views.
 *  4. Must not contain dangerous keywords (extra defense in depth).
 *  5. Must include a LIMIT, and that LIMIT must not exceed the configured cap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlValidator {

    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|grant|revoke|" +
            "copy|call|exec|execute|merge|vacuum|analyze|cluster|listen|notify|" +
            "lock|reindex|reset|set|do|comment|security|pg_sleep|pg_read_file|" +
            "pg_ls_dir|current_setting)\\b",
            Pattern.CASE_INSENSITIVE);

    private final PortfolioSqlProperties props;

    public ValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.fail("Empty SQL");
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        // 0. Multiple statements forbidden
        if (trimmed.contains(";")) {
            return ValidationResult.fail("Multiple SQL statements are not allowed");
        }

        // 1. Forbidden keywords (cheap pre-check before parsing)
        if (FORBIDDEN_KEYWORDS.matcher(trimmed).find()) {
            return ValidationResult.fail("SQL contains forbidden keyword");
        }

        // 2. Parse to AST
        Statement stmt;
        try {
            Statements stmts = CCJSqlParserUtil.parseStatements(trimmed);
            if (stmts.getStatements().size() != 1) {
                return ValidationResult.fail("Exactly one statement is required");
            }
            stmt = stmts.getStatements().get(0);
        } catch (JSQLParserException e) {
            log.warn("SQL parse failed: {}", e.getMessage());
            return ValidationResult.fail("SQL is not parseable: " + e.getMessage());
        }

        // 3. Must be SELECT
        if (!(stmt instanceof Select select)) {
            return ValidationResult.fail("Only SELECT statements are allowed");
        }

        // 4. Tables / views referenced must be in allow-list
        Set<String> allowed = props.getAllowedViews().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        List<String> usedTables;
        try {
            usedTables = new TablesNamesFinder().getTableList((Statement) select);
        } catch (Exception e) {
            return ValidationResult.fail("Failed to extract table names: " + e.getMessage());
        }
        for (String t : usedTables) {
            String bare = stripSchemaAndQuotes(t).toLowerCase(Locale.ROOT);
            if (!allowed.contains(bare)) {
                return ValidationResult.fail("Table/view '" + t + "' is not allowed");
            }
        }

        // 5. Enforce LIMIT
        if (!(select.getSelectBody() instanceof PlainSelect plain)) {
            return ValidationResult.fail("Only simple SELECT statements are allowed (no UNION / WITH)");
        }
        int maxRows = props.getExecution().getMaxRows();
        if (plain.getLimit() == null || plain.getLimit().getRowCount() == null) {
            // Auto-append a safe default LIMIT
            plain.setLimit(buildLimit(props.getExecution().getDefaultLimit()));
        } else {
            // Cap the LIMIT if it exceeds max
            try {
                if (plain.getLimit().getRowCount() instanceof LongValue lv && lv.getValue() > maxRows) {
                    lv.setValue(maxRows);
                }
            } catch (Exception ignore) { /* leave as-is */ }
        }

        return ValidationResult.ok(plain.toString());
    }

    private static net.sf.jsqlparser.statement.select.Limit buildLimit(long n) {
        net.sf.jsqlparser.statement.select.Limit l = new net.sf.jsqlparser.statement.select.Limit();
        l.setRowCount(new LongValue(n));
        return l;
    }

    private static String stripSchemaAndQuotes(String tableName) {
        String s = tableName;
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(dot + 1);
        s = s.replace("\"", "").replace("`", "");
        return s;
    }

    public record ValidationResult(boolean ok, String safeSql, String error) {
        public static ValidationResult ok(String safeSql)  { return new ValidationResult(true, safeSql, null); }
        public static ValidationResult fail(String error)  { return new ValidationResult(false, null, error); }
    }
}
