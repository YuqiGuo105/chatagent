package com.example.mcp.portfoliosql.service;

import com.example.mcp.portfoliosql.config.PortfolioSqlProperties;
import com.example.mcp.portfoliosql.dto.PortfolioQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes validated SELECT statements against the read-only Portfolio datasource.
 *
 * - Caches results by SQL string for {@code result-ttl-minutes}.
 * - Enforces query timeout and a hard row cap (defense in depth on top of SQL LIMIT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final PortfolioSqlProperties props;

    @Cacheable(cacheNames = "queryResults", key = "#safeSql")
    public PortfolioQueryResult execute(String safeSql) {
        long start = System.currentTimeMillis();
        jdbcTemplate.setQueryTimeout(props.getExecution().getQueryTimeoutSeconds());
        int maxRows = props.getExecution().getMaxRows();

        Result agg = jdbcTemplate.query(safeSql, rs -> {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            List<String> cols = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                cols.add(md.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            boolean truncated = false;
            while (rs.next()) {
                if (rows.size() >= maxRows) {
                    truncated = true;
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    row.put(cols.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }
            return new Result(cols, rows, truncated);
        });

        long latency = System.currentTimeMillis() - start;
        log.debug("Executed SQL in {}ms ({} rows, truncated={}): {}",
                latency, agg.rows.size(), agg.truncated, safeSql);

        return new PortfolioQueryResult(
                safeSql,
                agg.cols,
                agg.rows,
                agg.rows.size(),
                agg.truncated,
                latency);
    }

    private record Result(List<String> cols, List<Map<String, Object>> rows, boolean truncated) {}
}
