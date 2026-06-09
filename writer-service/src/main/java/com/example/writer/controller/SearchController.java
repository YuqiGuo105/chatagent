package com.example.writer.controller;

import com.example.writer.dto.SearchResponseDto;
import com.example.writer.service.ContentSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public search endpoint — no auth required.
 * Searched by AdminTokenFilter only for paths under /api/admin/; this
 * endpoint lives at /api/search so it is always accessible.
 *
 * <pre>GET /api/search?q=spring&amp;source=blog&amp;limit=10&amp;offset=0</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ContentSearchService searchService;

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing query parameter: q"));
        }

        // Guard against absurd values
        int safeLimit  = Math.min(Math.max(limit, 1), 50);
        int safeOffset = Math.max(offset, 0);

        log.debug("GET /api/search q={} source={} limit={} offset={}", trimmed, source, safeLimit, safeOffset);

        SearchResponseDto resp = searchService.search(trimmed, source, safeLimit, safeOffset);
        return ResponseEntity.ok(resp);
    }
}
