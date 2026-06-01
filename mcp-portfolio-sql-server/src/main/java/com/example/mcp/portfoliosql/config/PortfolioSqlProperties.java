package com.example.mcp.portfoliosql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "portfolio-sql")
public class PortfolioSqlProperties {

    private List<String> allowedViews = List.of();
    private Execution execution = new Execution();
    private Cache cache = new Cache();

    @Data
    public static class Execution {
        private int maxRows = 50;
        private int queryTimeoutSeconds = 5;
        private int defaultLimit = 10;
    }

    @Data
    public static class Cache {
        private int metadataTtlMinutes = 60;
        private int resultTtlMinutes = 5;
        private int resultMaxSize = 200;
    }
}
