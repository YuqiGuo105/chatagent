package com.example.chatagent.tool.websearch;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * In-process Spring AI tool for Google Custom Search.
 * Wired by {@link com.example.chatagent.config.InProcessToolsConfig}.
 */
public class WebSearchTool {

    private final GoogleSearchService googleSearchService;
    private final HighlightService highlightService;

    public WebSearchTool(GoogleSearchService googleSearchService, HighlightService highlightService) {
        this.googleSearchService = googleSearchService;
        this.highlightService = highlightService;
    }

    @Tool(name = "web_search_with_highlight",
          description = "Search the web via Google Custom Search and highlight query keywords " +
                        "inside each result snippet using HTML <mark> tags.")
    public SearchResponse webSearch(
            @ToolParam(description = "Search query (required).") String query,
            @ToolParam(description = "Optional page content to bias keyword extraction.", required = false)
            String pageContent,
            @ToolParam(description = "Whether to wrap matched keywords in <mark> tags. Default true.",
                       required = false) Boolean enableHighlight) {
        boolean highlight = enableHighlight == null || enableHighlight;
        String keywordSource = (pageContent == null || pageContent.isBlank())
                ? query : query + " " + pageContent;
        List<String> keywords = highlightService.extractKeywords(keywordSource);
        List<SearchResponse.SearchResult> raw = googleSearchService.search(query);
        List<SearchResponse.SearchResult> processed = raw.stream()
                .map(r -> new SearchResponse.SearchResult(
                        r.title(), r.url(), r.snippet(),
                        highlight ? highlightService.highlight(r.snippet(), keywords) : r.snippet()))
                .toList();
        return new SearchResponse(query, keywords, processed.size(), processed);
    }
}
