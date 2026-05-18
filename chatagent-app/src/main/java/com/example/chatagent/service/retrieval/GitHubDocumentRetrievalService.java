package com.example.chatagent.service.retrieval;

import com.example.chatagent.config.GitHubProperties;
import com.example.chatagent.model.github.GitHubProjectDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Similarity search against {@code public.github_project_documents}, executed
 * with a raw SQL {@code <=>} cosine query so we don't compete with Spring AI's
 * single auto-configured {@code PgVectorStore} (which targets kb_documents).
 */
@Slf4j
@Service
public class GitHubDocumentRetrievalService {

    private static final String SEARCH_SQL_ANY = """
            SELECT id, repo_owner, repo_name, branch, file_path, file_type,
                   content, chunk_index,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM public.github_project_documents
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String SEARCH_SQL_BY_TYPE = """
            SELECT id, repo_owner, repo_name, branch, file_path, file_type,
                   content, chunk_index,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM public.github_project_documents
            WHERE embedding IS NOT NULL AND file_type = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbc;
    private final GitHubProperties props;

    public GitHubDocumentRetrievalService(EmbeddingModel embeddingModel,
                                          JdbcTemplate jdbc,
                                          GitHubProperties props) {
        this.embeddingModel = embeddingModel;
        this.jdbc = jdbc;
        this.props = props;
    }

    public List<GitHubProjectDocument> retrieve(String query) {
        return retrieve(query, null, props.getRetrieval().getTopK(),
                props.getRetrieval().getSimilarityThreshold());
    }

    public List<GitHubProjectDocument> retrieve(String query, String fileType) {
        return retrieve(query, fileType, props.getRetrieval().getTopK(),
                props.getRetrieval().getSimilarityThreshold());
    }

    public List<GitHubProjectDocument> retrieve(String query, String fileType,
                                                int topK, double threshold) {
        if (query == null || query.isBlank()) return List.of();
        float[] embedding;
        try {
            embedding = embeddingModel.embed(query);
        } catch (Exception e) {
            log.warn("Embedding query failed: {}", e.toString());
            return List.of();
        }
        String vector = toPgVectorLiteral(embedding);
        List<GitHubProjectDocument> rows;
        try {
            if (fileType != null && !fileType.isBlank()) {
                rows = jdbc.query(SEARCH_SQL_BY_TYPE,
                        (rs, i) -> mapRow(rs),
                        vector, fileType, vector, topK);
            } else {
                rows = jdbc.query(SEARCH_SQL_ANY,
                        (rs, i) -> mapRow(rs),
                        vector, vector, topK);
            }
        } catch (Exception e) {
            log.warn("GitHub similarity search failed: {}", e.toString());
            return List.of();
        }
        List<GitHubProjectDocument> filtered = new ArrayList<>(rows.size());
        for (GitHubProjectDocument row : rows) {
            if (row.getSimilarity() == null || row.getSimilarity() >= threshold) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private GitHubProjectDocument mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        GitHubProjectDocument d = new GitHubProjectDocument();
        d.setId(rs.getLong("id"));
        d.setRepoOwner(rs.getString("repo_owner"));
        d.setRepoName(rs.getString("repo_name"));
        d.setBranch(rs.getString("branch"));
        d.setFilePath(rs.getString("file_path"));
        d.setFileType(rs.getString("file_type"));
        d.setContent(rs.getString("content"));
        d.setChunkIndex(rs.getInt("chunk_index"));
        double sim = rs.getDouble("similarity");
        if (!rs.wasNull()) d.setSimilarity(sim);
        return d;
    }

    private String toPgVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
