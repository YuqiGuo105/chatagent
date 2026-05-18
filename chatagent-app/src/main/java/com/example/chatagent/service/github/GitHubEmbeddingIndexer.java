package com.example.chatagent.service.github;

import com.example.chatagent.model.github.GitHubProjectDocument;
import com.example.chatagent.model.github.GitHubRepositoryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Generates embeddings for chunked GitHub file content and upserts rows into
 * {@code public.github_project_documents} via plain JDBC (so we don't fight
 * Spring AI's auto-configured PgVectorStore which targets {@code kb_documents}).
 */
@Slf4j
@Service
public class GitHubEmbeddingIndexer {

    private static final String UPSERT_SQL = """
            INSERT INTO public.github_project_documents
                (repo_owner, repo_name, branch, file_path, file_type, content,
                 chunk_index, metadata, embedding, last_synced_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?, ?)
            ON CONFLICT (repo_owner, repo_name, file_path, chunk_index)
            DO UPDATE SET
                branch = EXCLUDED.branch,
                file_type = EXCLUDED.file_type,
                content = EXCLUDED.content,
                metadata = EXCLUDED.metadata,
                embedding = EXCLUDED.embedding,
                last_synced_at = EXCLUDED.last_synced_at,
                updated_at = EXCLUDED.updated_at
            """;

    private static final String DELETE_STALE_SQL = """
            DELETE FROM public.github_project_documents
            WHERE repo_owner = ? AND repo_name = ? AND last_synced_at < ?
            """;

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbc;

    public GitHubEmbeddingIndexer(EmbeddingModel embeddingModel, JdbcTemplate jdbc) {
        this.embeddingModel = embeddingModel;
        this.jdbc = jdbc;
    }

    @Transactional
    public int indexChunks(GitHubRepositoryConfig repo,
                           String filePath,
                           String fileType,
                           List<String> chunks,
                           LocalDateTime syncStamp) {
        if (chunks == null || chunks.isEmpty()) return 0;
        int indexed = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (chunk == null || chunk.isBlank()) continue;
            float[] embedding;
            try {
                embedding = embeddingModel.embed(chunk);
            } catch (Exception e) {
                log.warn("Embedding failed for {} chunk {}: {}", filePath, i, e.toString());
                continue;
            }
            String vector = toPgVectorLiteral(embedding);
            String metadata = String.format("{\"repo\":\"%s/%s\",\"path\":\"%s\",\"chunk\":%d}",
                    escapeJson(repo.getOwner()), escapeJson(repo.getName()),
                    escapeJson(filePath), i);
            Timestamp ts = Timestamp.valueOf(syncStamp);
            jdbc.update(UPSERT_SQL,
                    repo.getOwner(), repo.getName(), repo.getBranch(),
                    filePath, fileType, chunk,
                    i, metadata, vector, ts, ts);
            indexed++;
        }
        return indexed;
    }

    /** Removes rows for the repo that weren't touched by this sync run (deleted files). */
    @Transactional
    public int pruneStale(GitHubRepositoryConfig repo, LocalDateTime syncStamp) {
        return jdbc.update(DELETE_STALE_SQL, repo.getOwner(), repo.getName(), Timestamp.valueOf(syncStamp));
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
