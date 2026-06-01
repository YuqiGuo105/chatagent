package com.example.chatagent.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires external MCP servers as a {@link ToolCallbackProvider} when
 * {@code spring.ai.mcp.client.enabled=true} and external servers are running.
 * Only active when at least one {@link McpAsyncClient} bean is present.
 */
@Slf4j
@Configuration
@ConditionalOnBean(McpAsyncClient.class)
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(List<McpAsyncClient> mcpAsyncClients) {
        log.info("Registering {} MCP async client(s) as tool callbacks", mcpAsyncClients.size());
        mcpAsyncClients.forEach(c ->
                log.info("  - MCP client: {}", c.getClientInfo() == null ? "?" : c.getClientInfo().name()));
        return new AsyncMcpToolCallbackProvider(mcpAsyncClients);
    }
}
