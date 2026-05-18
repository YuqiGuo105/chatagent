package com.example.chatagent.controller;

import com.example.chatagent.model.github.GitHubRepositoryConfig;
import com.example.chatagent.service.github.GitHubRepoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight admin endpoint for triggering an on-demand GitHub repo sync.
 * Intended for local / authenticated operator use — protect via a reverse
 * proxy / network policy in production.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/github")
@RequiredArgsConstructor
public class GitHubAdminController {

    private final GitHubRepoSyncService syncService;

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        GitHubRepoSyncService.SyncReport report = syncService.syncAll();
        return ResponseEntity.ok(reportMap(report));
    }

    @PostMapping("/sync/one")
    public ResponseEntity<Map<String, Object>> syncOne(@RequestParam String owner,
                                                       @RequestParam String name,
                                                       @RequestParam(defaultValue = "main") String branch) {
        GitHubRepositoryConfig repo = new GitHubRepositoryConfig();
        repo.setOwner(owner);
        repo.setName(name);
        repo.setBranch(branch);
        try {
            GitHubRepoSyncService.SyncReport report = syncService.syncRepository(repo);
            return ResponseEntity.ok(reportMap(report));
        } catch (IOException e) {
            log.error("Manual sync failed for {}/{}: {}", owner, name, e.toString());
            return ResponseEntity.internalServerError().body(Map.of("error", e.toString()));
        }
    }

    private Map<String, Object> reportMap(GitHubRepoSyncService.SyncReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filesIndexed", r.filesIndexed);
        m.put("chunksIndexed", r.chunksIndexed);
        m.put("filesSkipped", r.filesSkipped);
        m.put("chunksPruned", r.chunksPruned);
        m.put("errors", r.errors);
        return m;
    }
}
