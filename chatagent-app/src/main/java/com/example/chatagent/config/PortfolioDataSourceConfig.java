package com.example.chatagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Creates a {@link JdbcTemplate} ("portfolioJdbc") for portfolio data queries.
 *
 * <p>Two modes:
 * <ol>
 *   <li><b>Separate DB</b> — when {@code PORTFOLIO_DB_URL} is set, a dedicated
 *       HikariCP pool is created pointing at that URL (e.g. a separate Supabase
 *       project).</li>
 *   <li><b>Shared DB (default)</b> — when {@code PORTFOLIO_DB_URL} is absent,
 *       the main Spring Boot {@link DataSource} is reused. This is the normal
 *       case when both RAG and portfolio data live in the same Supabase DB
 *       (i.e. {@code SPRING_DATASOURCE_URL} already points at Supabase).</li>
 * </ol>
 */
@Slf4j
@Configuration
public class PortfolioDataSourceConfig {

    // ── Mode 1: dedicated portfolio DB ────────────────────────────────────────

    @Bean("portfolioDataSource")
    @ConditionalOnExpression("!'${PORTFOLIO_DB_URL:}'.isEmpty()")
    public DataSource portfolioDataSource(
            @Value("${PORTFOLIO_DB_URL}") String url,
            @Value("${PORTFOLIO_DB_USER:postgres}") String user,
            @Value("${PORTFOLIO_DB_PASSWORD:postgres}") String password) {
        log.info("Portfolio DataSource: dedicated pool → {}", url.replaceAll("password=[^&]*", "password=***"));
        org.springframework.boot.jdbc.DataSourceBuilder<?> builder =
                org.springframework.boot.jdbc.DataSourceBuilder.create();
        builder.url(url);
        builder.username(user);
        builder.password(password);
        builder.driverClassName("org.postgresql.Driver");
        return builder.build();
    }

    @Bean("portfolioJdbc")
    @ConditionalOnExpression("!'${PORTFOLIO_DB_URL:}'.isEmpty()")
    public JdbcTemplate portfolioJdbcDedicated(
            @Qualifier("portfolioDataSource") DataSource portfolioDataSource) {
        log.info("portfolioJdbc → dedicated DataSource");
        return new JdbcTemplate(portfolioDataSource);
    }

    // ── Mode 2: reuse main datasource (Supabase unified) ──────────────────────

    /**
     * When PORTFOLIO_DB_URL is absent, "portfolioJdbc" simply wraps the
     * primary DataSource.  The portfolio views (v_portfolio_*) must exist
     * in the same database as {@code SPRING_DATASOURCE_URL}.
     */
    @Bean("portfolioJdbc")
    @ConditionalOnExpression("'${PORTFOLIO_DB_URL:}'.isEmpty()")
    public JdbcTemplate portfolioJdbcShared(DataSource dataSource) {
        log.info("portfolioJdbc → shared main DataSource (Supabase unified mode)");
        return new JdbcTemplate(dataSource);
    }
}
