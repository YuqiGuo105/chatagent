package com.example.mcp.portfoliosql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class McpPortfolioSqlApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpPortfolioSqlApplication.class, args);
    }
}
