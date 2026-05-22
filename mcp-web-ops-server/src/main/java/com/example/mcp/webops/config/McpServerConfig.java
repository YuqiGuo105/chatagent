package com.example.mcp.webops.config;

import com.example.mcp.webops.tool.WebOpsTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider webOpsTools(WebOpsTool tool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build();
    }
}
