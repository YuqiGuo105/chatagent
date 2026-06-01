package com.example.mcp.portfoliosql.config;

import com.example.mcp.portfoliosql.tool.PortfolioSqlTool;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider portfolioSqlTools(PortfolioSqlTool portfolioSqlTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(portfolioSqlTool)
                .build();
    }

    @Bean
    public CacheManager cacheManager(PortfolioSqlProperties props) {
        CaffeineCacheManager mgr = new CaffeineCacheManager("queryResults", "viewMetadata");
        mgr.setCaffeine(Caffeine.newBuilder()
                .maximumSize(props.getCache().getResultMaxSize())
                .expireAfterWrite(props.getCache().getResultTtlMinutes(), TimeUnit.MINUTES));
        return mgr;
    }
}
