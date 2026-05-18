package com.example.chatagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin wrapper around the Spring AI {@link VectorStore} backed by pgvector.
 * Encapsulates similarity search + ingest so the rest of the application is
 * decoupled from Spring AI's API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final VectorStore vectorStore;

    @Value("${app.rag.top-k:6}")
    private int defaultTopK;

    @Value("${app.rag.similarity-threshold:0.50}")
    private double similarityThreshold;

    public List<Document> search(String query) {
        return search(query, defaultTopK);
    }

    public List<Document> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        try {
            List<Document> hits = vectorStore.similaritySearch(req);
            return hits == null ? List.of() : hits;
        } catch (Exception e) {
            log.warn("KB similarity search failed: {}", e.toString());
            return List.of();
        }
    }

    public void ingest(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return;
        vectorStore.add(docs);
    }
}
