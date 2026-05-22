package com.example.mcp.websearch.tool;

import com.example.mcp.websearch.dto.SearchResponse;
import com.example.mcp.websearch.service.GoogleSearchService;
import com.example.mcp.websearch.service.HighlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSearchTool {

    private final GoogleSearchService googleSearchService;
    private final HighlightService highlightService;

    @Tool(name = "web_search_with_highlight",
          description = "Search the web via Google Custom Search and highlight query keywords " +
                        "inside each result snippet using HTML <mark> tags.")
    public SearchResponse webSearch(
            @ToolParam(description = "Search query (required).") String query,
            @ToolParam(description = "Optional contextual page content to bias keyword extraction.",
                       required = false) String pageContent,
            @ToolParam(description = "Whether to wrap matched keywords in <mark> tags. Default true.",
                       required = false) Boolean enableHighlight
    ) {
        boolean highlight = enableHighlight == null || enableHighlight;
        String keywordSource = (pageContent == null || pageContent.isBlank())
                ? query
                : query + " " + pageContent;
        List<String> keywords = highlightService.extractKeywords(keywordSource);
        List<SearchResponse.SearchResult> raw = googleSearchService.search(query);

        List<SearchResponse.SearchResult> processed = raw.stream()
                .map(r -> new SearchResponse.SearchResult(
                        r.title(),
                        r.url(),
                        r.snippet(),
                        highlight ? highlightService.highlight(r.snippet(), keywords) : r.snippet()))
                .toList();

        return new SearchResponse(query, keywords, processed.size(), processed);
    }
}
