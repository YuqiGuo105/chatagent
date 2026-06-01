package com.example.mcp.portfoliosql.dto;

import java.util.List;

/**
 * Cached metadata about an allow-listed view: column names and types.
 * Used to build the NL2SQL system prompt.
 */
public record ViewMetadata(
        String viewName,
        List<Column> columns,
        String description
) {
    public record Column(String name, String type) {}
}
