package com.example.chatagent.tool.websearch;

import java.util.List;

public record SearchResponse(
        String query,
        List<String> keywords,
        int totalResults,
        List<SearchResult> results
) {
    public record SearchResult(
            String title,
            String url,
            String snippet,
            String highlightedSnippet
    ) {}
}
