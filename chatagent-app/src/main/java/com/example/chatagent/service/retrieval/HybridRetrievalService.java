package com.example.chatagent.service.retrieval;

import com.example.chatagent.model.github.GitHubProjectDocument;
import com.example.chatagent.service.KnowledgeBaseService;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Convenience facade that pulls context from both the curated KB and the
 * GitHub project index. Used when the router picks {@code hybrid}.
 */
@Service
public class HybridRetrievalService {

    public record HybridResult(List<Document> kb, List<GitHubProjectDocument> github) {}

    private final KnowledgeBaseService kbService;
    private final GitHubDocumentRetrievalService githubService;

    public HybridRetrievalService(KnowledgeBaseService kbService,
                                  GitHubDocumentRetrievalService githubService) {
        this.kbService = kbService;
        this.githubService = githubService;
    }

    public HybridResult retrieve(String query) {
        List<Document> kb = kbService.search(query);
        List<GitHubProjectDocument> github = githubService.retrieve(query);
        return new HybridResult(kb, github);
    }
}
