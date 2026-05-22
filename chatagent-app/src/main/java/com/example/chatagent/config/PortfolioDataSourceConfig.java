package com.example.chatagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Creates a secondary {@link JdbcTemplate} for the Portfolio Supabase database
 * only when the {@code PORTFOLIO_DB_URL} environment variable is present.
 *
 * <p>Set these env vars on Railway to enable visitor-analytics and blog tools:
 * <ul>
 *   <li>{@code PORTFOLIO_DB_URL}  — full JDBC URL, e.g.
 *       {@code jdbc:postgresql://db.xxx.supabase.co:5432/postgres}</li>
 *   <li>{@code PORTFOLIO_DB_USER} — default {@code postgres}</li>
 *   <li>{@code PORTFOLIO_DB_PASSWORD}</li>
 * </ul>
 */
@Slf4j
@Configuration
public class PortfolioDataSourceConfig {

    /**
     * Only created when PORTFOLIO_DB_URL is set and non-empty.
     * Uses a simple HikariCP pool via Spring Boot's DataSourceBuilder.
     */
    @Bean("portfolioDataSource")
    @ConditionalOnExpression("!'${PORTFOLIO_DB_URL:}'.isEmpty()")
    public DataSource portfolioDataSource(
            @Value("${PORTFOLIO_DB_URL}") String url,
            @Value("${PORTFOLIO_DB_USER:postgres}") String user,
            @Value("${PORTFOLIO_DB_PASSWORD:postgres}") String password) {
        log.info("Portfolio DataSource configured — connecting to Supabase portfolio DB");
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
    public JdbcTemplate portfolioJdbc(
            @Qualifier("portfolioDataSource") DataSource portfolioDataSource) {
        return new JdbcTemplate(portfolioDataSource);
    }
}
