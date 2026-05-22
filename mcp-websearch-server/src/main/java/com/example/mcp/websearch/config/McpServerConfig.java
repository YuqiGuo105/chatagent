package com.example.mcp.websearch.config;

import com.example.mcp.websearch.tool.WebSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider webSearchTools(WebSearchTool webSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(webSearchTool)
                .build();
    }
}
