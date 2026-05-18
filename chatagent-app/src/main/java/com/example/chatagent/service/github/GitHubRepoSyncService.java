package com.example.chatagent.service.github;

import com.example.chatagent.config.GitHubProperties;
import com.example.chatagent.model.github.GitHubRepositoryConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pulls configured GitHub repositories, walks their file tree, filters &
 * chunks files, then asks {@link GitHubEmbeddingIndexer} to embed and persist
 * the chunks.
 *
 * <p>Triggered by {@link Scheduled @Scheduled} (controlled by
 * {@code github.sync.cron}) and also exposed via {@link #syncAll()} for an
 * admin REST endpoint.</p>
 */
@Slf4j
@Service
public class GitHubRepoSyncService {

    private final GitHubProperties props;
    private final GitHubFileFilter fileFilter;
    private final GitHubChunkingService chunkingService;
    private final GitHubEmbeddingIndexer indexer;

    public GitHubRepoSyncService(GitHubProperties props,
                                 GitHubFileFilter fileFilter,
                                 GitHubChunkingService chunkingService,
                                 GitHubEmbeddingIndexer indexer) {
        this.props = props;
        this.fileFilter = fileFilter;
        this.chunkingService = chunkingService;
        this.indexer = indexer;
    }

    @PostConstruct
    public void logConfig() {
        List<String> repoNames = props.getRepositories().stream()
                .map(GitHubRepositoryConfig::toString)
                .collect(Collectors.toList());
        log.info("GitHub sync configured: enabled={} cron='{}' repos={}",
                props.getSync().isEnabled(), props.getSync().getCron(), repoNames);
    }

    @Scheduled(cron = "${github.sync.cron:0 0 2 * * *}")
    public void scheduledSync() {
        if (!props.getSync().isEnabled()) {
            log.debug("Scheduled GitHub sync skipped — github.sync.enabled=false");
            return;
        }
        syncAll();
    }

    /** Public entry-point used by the scheduler and the admin REST endpoint. */
    public SyncReport syncAll() {
        SyncReport overall = new SyncReport();
        if (props.getRepositories() == null || props.getRepositories().isEmpty()) {
            log.warn("No GitHub repositories configured — nothing to sync.");
            return overall;
        }
        GitHub github;
        try {
            github = buildClient();
        } catch (IOException e) {
            log.error("Failed to initialise GitHub client: {}", e.toString());
            overall.errors++;
            return overall;
        }
        for (GitHubRepositoryConfig repo : props.getRepositories()) {
            try {
                SyncReport r = syncRepository(github, repo);
                overall.merge(r);
            } catch (Exception e) {
                log.error("Sync failed for {}: {}", repo, e.toString());
                overall.errors++;
            }
        }
        log.info("GitHub sync complete: {}", overall);
        return overall;
    }

    public SyncReport syncRepository(GitHubRepositoryConfig repo) throws IOException {
        return syncRepository(buildClient(), repo);
    }

    private SyncReport syncRepository(GitHub github, GitHubRepositoryConfig repo) throws IOException {
        SyncReport report = new SyncReport();
        LocalDateTime stamp = LocalDateTime.now();
        String slug = repo.getOwner() + "/" + repo.getName();
        log.info("Syncing GitHub repo {}@{}", slug, repo.getBranch());

        GHRepository ghRepo = github.getRepository(slug);
        GHTree tree = ghRepo.getTreeRecursive(repo.getBranch(), 1);
        long maxSize = props.getSync().getMaxFileSize();

        for (GHTreeEntry entry : tree.getTree()) {
            if (!"blob".equals(entry.getType())) continue;
            String path = entry.getPath();
            if (!fileFilter.shouldInclude(path)) continue;
            if (entry.getSize() > maxSize) {
                log.debug("Skip {} ({} bytes > {})", path, entry.getSize(), maxSize);
                report.filesSkipped++;
                continue;
            }
            String content = downloadBlob(ghRepo, repo.getBranch(), path);
            if (content == null || content.isBlank()) {
                report.filesSkipped++;
                continue;
            }
            String type = fileFilter.getFileType(path);
            List<String> chunks = chunkingService.chunk(content, type);
            int indexed = indexer.indexChunks(repo, path, type, chunks, stamp);
            report.filesIndexed++;
            report.chunksIndexed += indexed;
        }
        int pruned = indexer.pruneStale(repo, stamp);
        report.chunksPruned += pruned;
        log.info("Repo {} synced: files={} chunks={} skipped={} pruned={}",
                slug, report.filesIndexed, report.chunksIndexed, report.filesSkipped, pruned);
        return report;
    }

    private String downloadBlob(GHRepository repo, String branch, String path) {
        try (InputStream in = repo.getFileContent(path, branch).read();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            log.warn("Failed to fetch {}@{}:{} — {}", repo.getFullName(), branch, path, e.toString());
            return null;
        }
    }

    private GitHub buildClient() throws IOException {
        GitHubBuilder builder = new GitHubBuilder();
        if (props.getToken() != null && !props.getToken().isBlank()) {
            builder.withOAuthToken(props.getToken());
        }
        return builder.build();
    }

    /** Tiny aggregation used for logging + REST responses. */
    public static class SyncReport {
        public int filesIndexed;
        public int chunksIndexed;
        public int filesSkipped;
        public int chunksPruned;
        public int errors;

        public void merge(SyncReport other) {
            this.filesIndexed += other.filesIndexed;
            this.chunksIndexed += other.chunksIndexed;
            this.filesSkipped += other.filesSkipped;
            this.chunksPruned += other.chunksPruned;
            this.errors += other.errors;
        }

        @Override
        public String toString() {
            return "filesIndexed=" + filesIndexed
                    + " chunksIndexed=" + chunksIndexed
                    + " filesSkipped=" + filesSkipped
                    + " chunksPruned=" + chunksPruned
                    + " errors=" + errors;
        }
    }
}
