package com.example.mcp.portfoliosql.service;

import com.example.mcp.portfoliosql.config.PortfolioSqlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlValidatorTest {

    private SqlValidator validator;

    @BeforeEach
    void setUp() {
        PortfolioSqlProperties props = new PortfolioSqlProperties();
        props.setAllowedViews(List.of(
                "v_portfolio_projects",
                "v_portfolio_tech_blogs",
                "v_portfolio_life_blogs",
                "v_portfolio_experience"));
        validator = new SqlValidator(props);
    }

    @Test
    void acceptsSimpleSelectAndAppendsDefaultLimit() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT title FROM v_portfolio_projects");
        assertTrue(r.ok(), r.error());
        assertTrue(r.safeSql().toLowerCase().contains("limit"));
    }

    @Test
    void acceptsExplicitLimit() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT title FROM v_portfolio_projects LIMIT 5");
        assertTrue(r.ok());
        assertTrue(r.safeSql().contains("5"));
    }

    @Test
    void capsExcessiveLimit() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT title FROM v_portfolio_projects LIMIT 9999");
        assertTrue(r.ok());
        assertFalse(r.safeSql().contains("9999"));
    }

    @Test
    void rejectsInsert() {
        SqlValidator.ValidationResult r = validator.validate(
                "INSERT INTO v_portfolio_projects(title) VALUES ('x')");
        assertFalse(r.ok());
    }

    @Test
    void rejectsUpdate() {
        assertFalse(validator.validate(
                "UPDATE v_portfolio_projects SET title='x'").ok());
    }

    @Test
    void rejectsDelete() {
        assertFalse(validator.validate(
                "DELETE FROM v_portfolio_projects").ok());
    }

    @Test
    void rejectsDrop() {
        assertFalse(validator.validate("DROP TABLE v_portfolio_projects").ok());
    }

    @Test
    void rejectsNonAllowedTable() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT * FROM pg_user");
        assertFalse(r.ok());
    }

    @Test
    void rejectsTableThatIsNotAView() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT * FROM \"Projects\"");
        assertFalse(r.ok());
    }

    @Test
    void rejectsMultipleStatements() {
        assertFalse(validator.validate(
                "SELECT 1 FROM v_portfolio_projects; SELECT 1 FROM v_portfolio_projects").ok());
    }

    @Test
    void rejectsUnion() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT title FROM v_portfolio_projects UNION SELECT title FROM v_portfolio_tech_blogs");
        assertFalse(r.ok());
    }

    @Test
    void rejectsPgSleep() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT pg_sleep(10) FROM v_portfolio_projects");
        assertFalse(r.ok());
    }

    @Test
    void rejectsEmpty() {
        assertFalse(validator.validate("").ok());
        assertFalse(validator.validate(null).ok());
    }

    @Test
    void acceptsWhereAndOrderBy() {
        SqlValidator.ValidationResult r = validator.validate(
                "SELECT title, tech_stack FROM v_portfolio_projects " +
                "WHERE category ILIKE '%ai%' ORDER BY published_at DESC LIMIT 10");
        assertTrue(r.ok(), r.error());
    }
}
