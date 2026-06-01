package com.example.mcp.portfoliosql.service;

import com.example.mcp.portfoliosql.config.PortfolioSqlProperties;
import com.example.mcp.portfoliosql.dto.ViewMetadata;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads column metadata for all allow-listed views once at startup.
 * The metadata feeds the NL2SQL system prompt so the LLM knows the schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewMetadataCache {

    private final JdbcTemplate jdbcTemplate;
    private final PortfolioSqlProperties props;

    private final AtomicReference<List<ViewMetadata>> cache = new AtomicReference<>(List.of());

    @PostConstruct
    public void load() {
        List<ViewMetadata> metas = new ArrayList<>();
        for (String view : props.getAllowedViews()) {
            try {
                ViewMetadata m = jdbcTemplate.execute((java.sql.Connection con) -> {
                    DatabaseMetaData dbm = con.getMetaData();
                    List<ViewMetadata.Column> cols = new ArrayList<>();
                    String desc = null;
                    try (ResultSet rs = dbm.getColumns(null, "public", view, "%")) {
                        while (rs.next()) {
                            cols.add(new ViewMetadata.Column(
                                    rs.getString("COLUMN_NAME"),
                                    rs.getString("TYPE_NAME")));
                        }
                    }
                    try (ResultSet rs = dbm.getTables(null, "public", view, new String[]{"VIEW"})) {
                        if (rs.next()) {
                            desc = rs.getString("REMARKS");
                        }
                    }
                    return new ViewMetadata(view, cols, desc);
                });
                if (m != null && !m.columns().isEmpty()) {
                    metas.add(m);
                    log.info("Loaded metadata for view {} ({} columns)", view, m.columns().size());
                } else {
                    log.warn("View {} has no columns or does not exist", view);
                }
            } catch (Exception e) {
                log.error("Failed to load metadata for view {}: {}", view, e.getMessage());
            }
        }
        cache.set(metas);
    }

    public List<ViewMetadata> getAll() {
        return cache.get();
    }
}
