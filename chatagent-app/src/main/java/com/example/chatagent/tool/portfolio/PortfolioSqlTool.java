package com.example.chatagent.tool.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

/**
 * In-process tool that lets the LLM query the portfolio owner's database.
 *
 * The LLM is expected to call this tool autonomously when the user's question
 * concerns the portfolio owner's own projects, blogs, work experience, etc.
 *
 * <p>This class is NOT a Spring component — it is registered as a {@code @Bean}
 * in {@link com.example.chatagent.config.InProcessToolsConfig} and added to the
 * {@link org.springframework.ai.chat.client.ChatClient} default tools.</p>
 */
@Slf4j
public class PortfolioSqlTool {

    private final NL2SqlService nl2SqlService;
    private final SqlValidator sqlValidator;
    private final QueryExecutor queryExecutor;

    public PortfolioSqlTool(NL2SqlService nl2SqlService,
                             SqlValidator sqlValidator,
                             QueryExecutor queryExecutor) {
        this.nl2SqlService = nl2SqlService;
        this.sqlValidator = sqlValidator;
        this.queryExecutor = queryExecutor;
    }

    @Tool(name = "query_portfolio_database",
          description = """
                  Query the portfolio owner's database to answer questions about \
                  their PROJECTS, TECH BLOG POSTS, LIFE BLOG POSTS, or WORK/EDUCATION EXPERIENCE.

                  Use this tool whenever the user asks about the owner's:
                    * personal projects, tech stack, project URLs, categories, years
                    * tech blog posts, summaries, tags
                    * life / personal blog posts, summaries, tags
                    * work experience, companies, roles, time periods

                  The input is a natural-language question; the tool will:
                    1) generate a safe read-only SQL query,
                    2) validate it against a strict allow-list of views,
                    3) execute it with a row cap and timeout,
                    4) return the generated SQL plus structured rows.

                  Do NOT use this tool for general web search, news, or external lookups \
                  (use a web search tool instead). Do NOT use it for site-visitor analytics \
                  (use the analytics tool instead).
                  """)
    public PortfolioQueryResult queryPortfolioDatabase(
            @ToolParam(description =
                    "The user's natural-language question, in any language. Required.")
            String question,
            @ToolParam(required = false, description =
                    "Optional hint to scope the search to one of: " +
                    "'projects' | 'tech_blogs' | 'life_blogs' | 'experience'. " +
                    "Leave empty if unsure.")
            String scopeHint
    ) {
        log.info("query_portfolio_database called: question='{}', scope='{}'", question, scopeHint);

        String rawSql;
        try {
            rawSql = nl2SqlService.generateSql(question, scopeHint);
        } catch (Exception e) {
            log.error("NL2SQL generation failed", e);
            return error("Failed to generate SQL: " + e.getMessage());
        }
        log.debug("Generated SQL: {}", rawSql);

        SqlValidator.ValidationResult v = sqlValidator.validate(rawSql);
        if (!v.ok()) {
            log.warn("SQL rejected by validator: {} -- sql: {}", v.error(), rawSql);
            return error("Generated SQL was rejected: " + v.error());
        }

        try {
            return queryExecutor.execute(v.safeSql());
        } catch (Exception e) {
            log.error("SQL execution failed for: {}", v.safeSql(), e);
            return error("SQL execution failed: " + e.getMessage());
        }
    }

    private static PortfolioQueryResult error(String message) {
        return new PortfolioQueryResult(
                null,
                List.of("error"),
                List.of(Map.of("error", message)),
                0,
                false,
                0L);
    }
}
