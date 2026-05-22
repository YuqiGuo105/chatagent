package com.example.mcp.analytics.config;

import com.example.mcp.analytics.tool.VisitorAnalyticsTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider visitorAnalyticsTools(VisitorAnalyticsTool tool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build();
    }
}
