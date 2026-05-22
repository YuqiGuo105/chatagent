package com.example.chatagent.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the three MCP servers (websearch / visitor-analytics / web-ops) as a single
 * {@link ToolCallbackProvider} that any {@code ChatClient.Builder} can inject via
 * {@code .defaultToolCallbacks(provider)}.
 *
 * The underlying {@link McpAsyncClient} beans are auto-configured by
 * {@code spring-ai-starter-mcp-client-webflux} from the {@code spring.ai.mcp.client.sse.connections}
 * map in application.yaml.
 */
@Slf4j
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(List<McpAsyncClient> mcpAsyncClients) {
        log.info("Registering {} MCP async client(s) as tool callbacks", mcpAsyncClients.size());
        mcpAsyncClients.forEach(c ->
                log.info("  - MCP client: {}", c.getClientInfo() == null ? "?" : c.getClientInfo().name()));
        return new AsyncMcpToolCallbackProvider(mcpAsyncClients);
    }
}
