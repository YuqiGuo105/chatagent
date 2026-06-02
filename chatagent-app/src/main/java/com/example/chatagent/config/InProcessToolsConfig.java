package com.example.chatagent.config;

import com.example.chatagent.tool.analytics.VisitorAnalyticsService;
import com.example.chatagent.tool.analytics.VisitorAnalyticsTool;
import com.example.chatagent.tool.concepts.KeyConceptExtractorTool;
import com.example.chatagent.tool.portfolio.NL2SqlService;
import com.example.chatagent.tool.portfolio.PortfolioSqlProperties;
import com.example.chatagent.tool.portfolio.PortfolioSqlTool;
import com.example.chatagent.tool.portfolio.QueryExecutor;
import com.example.chatagent.tool.portfolio.SqlValidator;
import com.example.chatagent.tool.portfolio.ViewMetadataCache;
import com.example.chatagent.tool.sitetour.SiteTourTool;
import com.example.chatagent.tool.webops.BlogService;
import com.example.chatagent.tool.webops.PerformanceService;
import com.example.chatagent.tool.webops.SeoService;
import com.example.chatagent.tool.webops.WebOpsTool;
import com.example.chatagent.tool.websearch.GoogleSearchService;
import com.example.chatagent.tool.websearch.HighlightService;
import com.example.chatagent.tool.websearch.WebSearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Creates in-process tool beans so the LLM can call visitor-analytics, blog CRUD,
 * SEO/performance checks, and Google web search directly — without requiring the
 * three separate MCP microservices to be deployed.
 *
 * <p>DB-dependent tools are conditional on {@link PortfolioDataSourceConfig}
 * having created the {@code portfolioJdbc} bean (i.e. {@code PORTFOLIO_DB_URL} is set).
 * WebSearch tools are conditional on {@code GOOGLE_API_KEY} being present.
 *
 * <p>{@code @Import(PortfolioDataSourceConfig.class)} ensures {@code portfolioJdbc}
 * is registered before {@code @ConditionalOnBean} is evaluated in this class —
 * without it, alphabetical scan order (I before P) causes the condition to always
 * be false.</p>
 */
@Slf4j
@Configuration
@Import(PortfolioDataSourceConfig.class)
public class InProcessToolsConfig {

    // ----------------------------------------------------------------
    // Analytics tools  (need portfolio DB)
    // ----------------------------------------------------------------

    @Bean
    @ConditionalOnBean(name = "portfolioJdbc")
    public VisitorAnalyticsService visitorAnalyticsService(
            @Qualifier("portfolioJdbc") JdbcTemplate portfolioJdbc) {
        log.info("Registering in-process VisitorAnalyticsService");
        return new VisitorAnalyticsService(portfolioJdbc);
    }

    @Bean
    @ConditionalOnBean(VisitorAnalyticsService.class)
    public VisitorAnalyticsTool visitorAnalyticsTool(VisitorAnalyticsService service) {
        return new VisitorAnalyticsTool(service);
    }

    // ----------------------------------------------------------------
    // Web-ops tools  (need portfolio DB)
    // ----------------------------------------------------------------

    @Bean
    @ConditionalOnBean(name = "portfolioJdbc")
    public BlogService portfolioBlogService(
            @Qualifier("portfolioJdbc") JdbcTemplate portfolioJdbc) {
        log.info("Registering in-process BlogService");
        return new BlogService(portfolioJdbc);
    }

    @Bean
    public SeoService seoService() {
        return new SeoService();
    }

    @Bean
    public PerformanceService performanceService() {
        return new PerformanceService();
    }

    @Bean
    @ConditionalOnBean(BlogService.class)
    public WebOpsTool webOpsTool(BlogService blogService,
                                 SeoService seoService,
                                 PerformanceService performanceService) {
        log.info("Registering in-process WebOpsTool");
        return new WebOpsTool(blogService, seoService, performanceService);
    }

    // ----------------------------------------------------------------
    // Web-search tools  (need Google API key)
    // ----------------------------------------------------------------

    @Bean
    @ConditionalOnExpression("!'${GOOGLE_API_KEY:}'.isEmpty()")
    public GoogleSearchService googleSearchService(
            @Value("${GOOGLE_API_KEY}") String apiKey,
            @Value("${GOOGLE_CSE_ID:}") String cx,
            @Value("${google.search.endpoint:https://www.googleapis.com/customsearch/v1}") String endpoint,
            @Value("${google.search.num-results:10}") int numResults) {
        log.info("Registering in-process GoogleSearchService");
        return new GoogleSearchService(apiKey, cx, endpoint, numResults);
    }

    @Bean
    public HighlightService highlightService() {
        return new HighlightService();
    }

    @Bean
    @ConditionalOnBean(GoogleSearchService.class)
    public WebSearchTool webSearchTool(GoogleSearchService googleSearchService,
                                       HighlightService highlightService) {
        log.info("Registering in-process WebSearchTool");
        return new WebSearchTool(googleSearchService, highlightService);
    }

    // ----------------------------------------------------------------
    // Site tour + concept tools  (always available)
    // ----------------------------------------------------------------

    @Bean
    public SiteTourTool siteTourTool(ChatClient.Builder chatClientBuilder,
                                     ObjectMapper objectMapper) {
        log.info("Registering in-process SiteTourTool");
        return new SiteTourTool(chatClientBuilder, objectMapper);
    }

    @Bean
    public KeyConceptExtractorTool keyConceptExtractorTool(ChatClient.Builder chatClientBuilder,
                                                           ObjectMapper objectMapper) {
        log.info("Registering in-process KeyConceptExtractorTool");
        return new KeyConceptExtractorTool(chatClientBuilder, objectMapper);
    }

    // ----------------------------------------------------------------
    // Portfolio SQL tools  (need portfolio DB)
    // ----------------------------------------------------------------

    @Bean
    public ViewMetadataCache viewMetadataCache(
            @Qualifier("portfolioJdbc") JdbcTemplate portfolioJdbc,
            PortfolioSqlProperties portfolioSqlProperties) {
        log.info("Registering in-process ViewMetadataCache");
        ViewMetadataCache cache = new ViewMetadataCache(portfolioJdbc, portfolioSqlProperties);
        cache.load();
        return cache;
    }

    @Bean
    public SqlValidator sqlValidator(PortfolioSqlProperties portfolioSqlProperties) {
        return new SqlValidator(portfolioSqlProperties);
    }

    @Bean
    public QueryExecutor queryExecutor(
            @Qualifier("portfolioJdbc") JdbcTemplate portfolioJdbc,
            PortfolioSqlProperties portfolioSqlProperties) {
        return new QueryExecutor(portfolioJdbc, portfolioSqlProperties);
    }

    @Bean
    public NL2SqlService nl2SqlService(
            ChatClient.Builder chatClientBuilder,
            ViewMetadataCache viewMetadataCache,
            PortfolioSqlProperties portfolioSqlProperties,
            @Value("classpath:prompts/nl2sql-system.txt") Resource systemPromptResource) throws IOException {
        log.info("Registering in-process NL2SqlService");
        String template = new String(systemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new NL2SqlService(chatClientBuilder, viewMetadataCache, portfolioSqlProperties, template);
    }

    @Bean
    public PortfolioSqlTool portfolioSqlTool(NL2SqlService nl2SqlService,
                                             SqlValidator sqlValidator,
                                             QueryExecutor queryExecutor) {
        log.info("Registering in-process PortfolioSqlTool");
        return new PortfolioSqlTool(nl2SqlService, sqlValidator, queryExecutor);
    }
}
