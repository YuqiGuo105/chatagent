package com.example.chatagent.tool.portfolio;

import java.util.List;

/**
 * Cached metadata about an allow-listed view: column names and types.
 */
public record ViewMetadata(
        String viewName,
        List<Column> columns,
        String description
) {
    public record Column(String name, String type) {}
}
