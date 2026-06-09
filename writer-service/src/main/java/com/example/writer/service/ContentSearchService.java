package com.example.writer.service;

import com.example.writer.dto.SearchResponseDto;
import com.example.writer.dto.SearchResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Full-text search across writer.blogs, writer.life_blogs, writer.projects.
 *
 * <p>Three search modes, applied in parallel with OR logic so results survive
 * partial matches:
 * <ol>
 *   <li><b>Keyword / FTS</b> — {@code websearch_to_tsquery} with English stemming
 *       (e.g. "blog" finds "blogging", "running" finds "run").</li>
 *   <li><b>Synonym / stem</b> — {@code plainto_tsquery} as a more lenient fallback
 *       that ignores websearch operators so plain phrases always work.</li>
 *   <li><b>Fuzzy</b> — {@code pg_trgm word_similarity} catches typos and partial
 *       words (e.g. "Kubernets" → "Kubernetes", "sprin" → "Spring Boot").</li>
 * </ol>
 * Rank = FTS rank × 2 + trigram similarity × 0.4, ensuring exact/semantic
 * matches float above fuzzy-only hits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSearchService {

    private final JdbcTemplate jdbc;

    /*
     * Params per query (positional ?):
     *  [1] q     — ts_rank FTS weight
     *  [2] q     — word_similarity trigram rank bonus
     *  [3] q     — websearch_to_tsquery  (keyword + phrase + operators)
     *  [4] q     — plainto_tsquery       (plain synonym / stem fallback)
     *  [5] q     — word_similarity > 0.2 (fuzzy typo match)
     *  [6] ilike — title ILIKE           (substring match)
     *  [7] ilike — tags  ILIKE           (substring match)
     *  [8] ilike — technology ILIKE      (projects only)
     */

    private static final String BLOG_SQL = """
            SELECT
                'blog'         AS source,
                'writer.blogs' AS source_table,
                id::text       AS source_id,
                title,
                description,
                COALESCE(url, '/blog-single/' || id::text) AS url,
                tags,
                TO_CHAR(published_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS published_at,
                (
                    ts_rank(
                        to_tsvector('english',
                            COALESCE(title,'') || ' ' ||
                            COALESCE(description,'') || ' ' ||
                            COALESCE(tags,'')),
                        websearch_to_tsquery('english', ?)
                    ) * 2.0
                    + word_similarity(?,
                        COALESCE(title,'') || ' ' || COALESCE(tags,'')
                      ) * 0.4
                ) AS rank
            FROM writer.blogs
            WHERE status = 'PUBLISHED'
              AND visibility = 'PUBLIC'
              AND (
                  to_tsvector('english',
                      COALESCE(title,'') || ' ' ||
                      COALESCE(description,'') || ' ' ||
                      COALESCE(tags,''))
                  @@ websearch_to_tsquery('english', ?)
                  OR
                  to_tsvector('english',
                      COALESCE(title,'') || ' ' ||
                      COALESCE(description,'') || ' ' ||
                      COALESCE(tags,''))
                  @@ plainto_tsquery('english', ?)
                  OR word_similarity(?,
                        COALESCE(title,'') || ' ' || COALESCE(tags,'')
                     ) > 0.2
                  OR title ILIKE ?
                  OR tags  ILIKE ?
              )
            """;

    private static final String LIFE_BLOG_SQL = """
            SELECT
                'life-blog'         AS source,
                'writer.life_blogs' AS source_table,
                id::text            AS source_id,
                title,
                description,
                COALESCE(url, '/life-blog/' || id::text) AS url,
                tags,
                TO_CHAR(published_at::timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS published_at,
                (
                    ts_rank(
                        to_tsvector('english',
                            COALESCE(title,'') || ' ' ||
                            COALESCE(description,'') || ' ' ||
                            COALESCE(tags,'')),
                        websearch_to_tsquery('english', ?)
                    ) * 2.0
                    + word_similarity(?,
                        COALESCE(title,'') || ' ' || COALESCE(tags,'')
                      ) * 0.4
                ) AS rank
            FROM writer.life_blogs
            WHERE status = 'PUBLISHED'
              AND (
                  to_tsvector('english',
                      COALESCE(title,'') || ' ' ||
                      COALESCE(description,'') || ' ' ||
                      COALESCE(tags,''))
                  @@ websearch_to_tsquery('english', ?)
                  OR
                  to_tsvector('english',
                      COALESCE(title,'') || ' ' ||
                      COALESCE(description,'') || ' ' ||
                      COALESCE(tags,''))
                  @@ plainto_tsquery('english', ?)
                  OR word_similarity(?,
                        COALESCE(title,'') || ' ' || COALESCE(tags,'')
                     ) > 0.2
                  OR title ILIKE ?
                  OR tags  ILIKE ?
              )
            """;

    private static final String PROJECT_SQL = """
            SELECT
                'project'            AS source,
                'writer.projects'    AS source_table,
                id::text             AS source_id,
                title,
                description,
                COALESCE(url, '/works') AS url,
                tags,
                TO_CHAR(published_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS published_at,
                (
                    ts_rank(
                        to_tsvector('english',
                            COALESCE(title,'') || ' ' ||
                            COALESCE(description,'') || ' ' ||
                            COALESCE(tags,'') || ' ' ||
                            COALESCE(technology,'')),
                        websearch_to_tsquery('english', ?)
                    ) * 2.0
                    + word_similarity(?,
                        COALESCE(title,'') || ' ' ||
                        COALESCE(tags,'') || ' ' ||
                        COALESCE(technology,'')
                      ) * 0.4
                ) AS rank
            FROM writer.projects
            WHERE status = 'PUBLISHED'
              AND visibility = 'PUBLIC'
              AND (
                  to_tsvector('english',
                      COALESCE(title,'') || ' ' ||
                      COALESCE(description,'') || ' ' ||
                      COALESCE(tags,'') || ' ' ||
                      COALESCE(technology,''))
                  @@ websearch_to_tsquery('english', ?)
                  OR
                  to_tsvector('english',
                      COALESCE(title,'') || ' ' ||
                      COALESCE(description,'') || ' ' ||
                      COALESCE(tags,'') || ' ' ||
                      COALESCE(technology,''))
                  @@ plainto_tsquery('english', ?)
                  OR word_similarity(?,
                        COALESCE(title,'') || ' ' ||
                        COALESCE(tags,'') || ' ' ||
                        COALESCE(technology,'')
                     ) > 0.2
                  OR title      ILIKE ?
                  OR tags       ILIKE ?
                  OR technology ILIKE ?
              )
            """;

    public SearchResponseDto search(String q, String source, int limit, int offset) {
        String ilike = "%" + q.toLowerCase() + "%";
        // blogs / life_blogs: 7 params [rank, trgm-rank, fts, plain, trgm-where, ilike, ilike]
        Object[] params = {q, q, q, q, q, ilike, ilike};
        // projects: 8 params (+ technology ILIKE)
        Object[] paramsProj = {q, q, q, q, q, ilike, ilike, ilike};

        List<SearchResultDto> all = new ArrayList<>();

        if (source == null || source.equals("blog")) {
            all.addAll(runQuery(BLOG_SQL, params));
        }
        if (source == null || source.equals("life-blog")) {
            all.addAll(runQuery(LIFE_BLOG_SQL, params));
        }
        if (source == null || source.equals("project")) {
            all.addAll(runQuery(PROJECT_SQL, paramsProj));
        }

        // Sort by FTS rank descending, then alphabetically by title for ties
        all.sort(Comparator.comparingDouble(SearchResultDto::getRank).reversed()
                .thenComparing(r -> r.getTitle() == null ? "" : r.getTitle()));

        long total = all.size();
        List<SearchResultDto> page = all.stream()
                .skip(offset)
                .limit(limit)
                .toList();

        return new SearchResponseDto(page, total, limit, offset);
    }

    private List<SearchResultDto> runQuery(String sql, Object[] params) {
        try {
            return jdbc.query(sql, params, (rs, rowNum) -> SearchResultDto.builder()
                    .source(rs.getString("source"))
                    .sourceTable(rs.getString("source_table"))
                    .sourceId(rs.getString("source_id"))
                    .title(rs.getString("title"))
                    .description(rs.getString("description"))
                    .url(rs.getString("url"))
                    .tags(rs.getString("tags"))
                    .publishedAt(rs.getString("published_at"))
                    .rank(rs.getDouble("rank"))
                    .build());
        } catch (Exception e) {
            log.warn("Search query failed for SQL segment: {}", e.getMessage());
            return List.of();
        }
    }
}
