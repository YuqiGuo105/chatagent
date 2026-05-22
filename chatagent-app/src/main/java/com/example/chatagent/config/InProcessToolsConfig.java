package com.example.chatagent.config;

import com.example.chatagent.tool.analytics.VisitorAnalyticsService;
import com.example.chatagent.tool.analytics.VisitorAnalyticsTool;
import com.example.chatagent.tool.webops.BlogService;
import com.example.chatagent.tool.webops.PerformanceService;
import com.example.chatagent.tool.webops.SeoService;
import com.example.chatagent.tool.webops.WebOpsTool;
import com.example.chatagent.tool.websearch.GoogleSearchService;
import com.example.chatagent.tool.websearch.HighlightService;
import com.example.chatagent.tool.websearch.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates in-process tool beans so the LLM can call visitor-analytics, blog CRUD,
 * SEO/performance checks, and Google web search directly — without requiring the
 * three separate MCP microservices to be deployed.
 *
 * <p>DB-dependent tools are conditional on {@link PortfolioDataSourceConfig}
 * having created the {@code portfolioJdbc} bean (i.e. {@code PORTFOLIO_DB_URL} is set).
 * WebSearch tools are conditional on {@code GOOGLE_API_KEY} being present.
 */
@Slf4j
@Configuration
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
}
