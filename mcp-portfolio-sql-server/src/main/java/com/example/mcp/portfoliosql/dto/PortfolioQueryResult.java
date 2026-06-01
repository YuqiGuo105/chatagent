package com.example.mcp.portfoliosql.dto;

import java.util.List;
import java.util.Map;

/**
 * Result returned to the caller (chatagent-app, via MCP tool call).
 * - `sql` is included so the upstream agent can show the generated query to the user.
 * - `rows` is a list of column->value maps for easy LLM consumption.
 */
public record PortfolioQueryResult(
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        boolean truncated,
        long latencyMs
) {}
