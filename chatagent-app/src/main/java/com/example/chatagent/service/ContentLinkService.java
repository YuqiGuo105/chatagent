package com.example.chatagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs a fresh semantic search against the vector store to suggest related content
 * (blog posts, projects) based on the user's question.
 *
 * <p>Unlike {@link SourceEnrichmentService} which enriches existing RAG hits via DB views,
 * this service runs an independent similarity search with a lower threshold (default 0.40)
 * to surface a broader set of potentially relevant links.  Results are deduplicated by
 * {@code (source, source_id)} and capped at {@code app.content-links.max-results}.</p>
 */
@Slf4j
@Service
public class ContentLinkService {

    private final VectorStore vectorStore;

    @Value("${app.content-links.enabled:true}")
    private boolean enabled;

    @Value("${app.content-links.max-results:3}")
    private int maxResults;

    @Value("${app.content-links.similarity-threshold:0.40}")
    private double similarityThreshold;

    public ContentLinkService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Searches the vector store for content related to {@code question} and returns
     * at most {@code app.content-links.max-results} deduplicated link descriptors.
     *
     * @return list of link maps, each containing: type, id, title, url, snippet, relevanceScore
     */
    public List<Map<String, Object>> findRelatedLinks(String question) {
        if (!enabled || question == null || question.isBlank()) return List.of();
        try {
            SearchRequest req = SearchRequest.builder()
                    .query(question)
                    .topK(maxResults * 3)          // overfetch to allow deduplication
                    .similarityThreshold(similarityThreshold)
                    .build();
            List<Document> docs = vectorStore.similaritySearch(req);
            if (docs == null || docs.isEmpty()) return List.of();

            // Dedup by (source|source_id) while preserving relevance order
            LinkedHashMap<String, Map<String, Object>> seen = new LinkedHashMap<>();
            for (Document doc : docs) {
                Map<String, Object> link = extractLinkMetadata(doc);
                if (link == null) continue;
                String key = link.get("type") + "|" + link.get("id");
                seen.putIfAbsent(key, link);
                if (seen.size() >= maxResults) break;
            }
            return new ArrayList<>(seen.values());
        } catch (Exception e) {
            log.warn("ContentLinkService.findRelatedLinks failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Derives a link descriptor from a {@link Document}'s metadata.
     * Returns {@code null} if the document cannot be mapped to a linkable content page.
     */
    Map<String, Object> extractLinkMetadata(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        if (meta == null) return null;

        String source   = metaStr(meta, "source");
        String sourceId = metaStr(meta, "source_id");
        if (source == null || sourceId == null) return null;

        // Resolve content type and URL from known source identifiers
        String type;
        String url;
        switch (source) {
            case "life_blogs" -> { type = "blog";    url = "/life-blog/"   + sourceId; }
            case "Blogs"      -> { type = "blog";    url = "/blog-single/" + sourceId; }
            case "Projects"   -> { type = "project"; url = "/work-single/" + sourceId; }
            default           -> { return null; }   // not a linkable source
        }

        // Title: prefer explicit metadata field, then first line of document text
        String title = metaStr(meta, "title");
        if (title == null) title = metaStr(meta, "name");
        if (title == null) {
            String docText = doc.getText();
            if (docText != null && !docText.isBlank()) {
                String firstLine = docText.strip().lines().findFirst().orElse("").trim();
                title = firstLine.isEmpty() ? sourceId
                        : firstLine.substring(0, Math.min(80, firstLine.length()));
            } else {
                title = sourceId;
            }
        }

        // Snippet: first 200 chars of document content
        String docText = doc.getText();
        String snippet = "";
        if (docText != null && !docText.isBlank()) {
            String clean = docText.strip().replaceAll("\\s+", " ");
            snippet = clean.substring(0, Math.min(200, clean.length()));
        }

        Double score = doc.getScore();

        Map<String, Object> link = new LinkedHashMap<>();
        link.put("type",           type);
        link.put("id",             sourceId);
        link.put("title",          title);
        link.put("url",            url);
        link.put("snippet",        snippet);
        link.put("relevanceScore", score != null ? score : 0.0);
        return link;
    }

    private static String metaStr(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        return v instanceof String s ? (s.isBlank() ? null : s) : (v != null ? v.toString() : null);
    }
}
