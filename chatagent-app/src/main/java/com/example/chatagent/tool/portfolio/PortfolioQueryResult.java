package com.example.chatagent.tool.portfolio;

import java.util.List;
import java.util.Map;

/**
 * Result of a portfolio SQL query executed in-process.
 */
public record PortfolioQueryResult(
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        boolean truncated,
        long latencyMs
) {}
