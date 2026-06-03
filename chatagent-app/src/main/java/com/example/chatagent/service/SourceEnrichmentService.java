package com.example.chatagent.service;

import com.example.chatagent.dto.SourceCardDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts RAG-retrieved {@link Document} chunks into enriched {@link SourceCardDto} objects
 * by joining against the portfolio views ({@code v_portfolio_life_blogs},
 * {@code v_portfolio_tech_blogs}, {@code v_portfolio_projects}).
 *
 * <p>Relevance is decided purely by the vector store's similarity score
 * ({@link Document#getScore()}): only chunks whose score meets
 * {@code chat.source-cards.min-score} (default {@code 0.75}) are eligible, and results are
 * deduplicated by ({@code source}, {@code source_id}) while preserving RAG ranking order.
 * This avoids any language-specific heuristics.</p>
 */
@Slf4j
@Service
public class SourceEnrichmentService {

    private static final Set<String> LINKABLE_SOURCES = Set.of("life_blogs", "Blogs", "Projects");

    private final JdbcTemplate portfolioJdbc;
    private final double minScore;

    public SourceEnrichmentService(@Qualifier("portfolioJdbc") JdbcTemplate portfolioJdbc,
                                   @Value("${chat.source-cards.min-score:0.75}") double minScore) {
        this.portfolioJdbc = portfolioJdbc;
        this.minScore = minScore;
    }

    /**
     * Enriches RAG documents into at most {@code maxCards} source cards.
     * Only documents whose similarity score is ≥ {@code chat.source-cards.min-score} are
     * eligible; if no score is present on a document, that document is skipped to avoid
     * showing potentially unrelated cards.
     */
    public List<SourceCardDto> enrich(List<Document> docs, int maxCards) {
        if (docs == null || docs.isEmpty()) return List.of();

        // Collect unique (source, source_id) pairs — preserve RAG ranking order
        // and require a similarity score above the configured threshold.
        LinkedHashMap<String, String> seen = new LinkedHashMap<>(); // key = "source|id"
        for (Document doc : docs) {
            Double score = doc.getScore();
            if (score == null || score < minScore) {
                log.debug("Skipping source card: score={} below min={}", score, minScore);
                continue;
            }
            Map<String, Object> meta = doc.getMetadata();
            String source   = metaStr(meta, "source");
            String sourceId = metaStr(meta, "source_id");
            if (source == null || sourceId == null) continue;
            if (!LINKABLE_SOURCES.contains(source)) continue;
            String key = source + "|" + sourceId;
            seen.putIfAbsent(key, sourceId);
            if (seen.size() >= maxCards) break;
        }

        if (seen.isEmpty()) return List.of();

        List<SourceCardDto> cards = new ArrayList<>();
        for (Map.Entry<String, String> entry : seen.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String source  = parts[0];
            String rawId   = parts[1];
            try {
                SourceCardDto card = lookupCard(source, rawId);
                if (card != null) cards.add(card);
            } catch (Exception ex) {
                log.warn("Source enrichment lookup failed for source={} id={}: {}", source, rawId, ex.getMessage());
            }
            if (cards.size() >= maxCards) break;
        }
        return cards;
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private SourceCardDto lookupCard(String source, String rawId) {
        return switch (source) {
            case "life_blogs" -> lookupLifeBlog(rawId);
            case "Blogs"      -> lookupTechBlog(rawId);
            case "Projects"   -> lookupProject(rawId);
            default           -> null;
        };
    }

    private SourceCardDto lookupLifeBlog(String rawId) {
        try {
            int id = Integer.parseInt(rawId.trim());
            List<SourceCardDto> rows = portfolioJdbc.query(
                    "SELECT id, title, image_url, published_at FROM v_portfolio_life_blogs WHERE id = ? LIMIT 1",
                    (rs, rn) -> new SourceCardDto(
                            String.valueOf(rs.getInt("id")),
                            "life_blog",
                            rs.getString("title"),
                            rs.getString("image_url"),
                            "/life-blog/" + rs.getInt("id"),
                            rs.getString("published_at")),
                    id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (NumberFormatException ex) {
            log.warn("life_blog source_id is not an integer: {}", rawId);
            return null;
        }
    }

    private SourceCardDto lookupTechBlog(String rawId) {
        List<SourceCardDto> rows = portfolioJdbc.query(
                "SELECT id::text, title, image_url, published_date FROM v_portfolio_tech_blogs WHERE id = ?::uuid LIMIT 1",
                (rs, rn) -> new SourceCardDto(
                        rs.getString("id"),
                        "tech_blog",
                        rs.getString("title"),
                        rs.getString("image_url"),
                        "/blog-single/" + rs.getString("id"),
                        rs.getString("published_date")),
                rawId.trim());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SourceCardDto lookupProject(String rawId) {
        List<SourceCardDto> rows = portfolioJdbc.query(
                "SELECT id::text, title, image_url, url, published_at FROM v_portfolio_projects WHERE id = ?::uuid LIMIT 1",
                (rs, rn) -> new SourceCardDto(
                        rs.getString("id"),
                        "project",
                        rs.getString("title"),
                        rs.getString("image_url"),
                        "/work-single/" + rs.getString("id"),
                        rs.getString("published_at")),
                rawId.trim());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String metaStr(Map<String, Object> meta, String key) {
        if (meta == null) return null;
        Object v = meta.get(key);
        return v instanceof String s ? (s.isBlank() ? null : s) : (v != null ? v.toString() : null);
    }
}
