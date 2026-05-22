package com.example.chatagent.tool.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around Google Custom Search JSON API.
 * Wired by {@link com.example.chatagent.config.InProcessToolsConfig}.
 */
@Slf4j
public class GoogleSearchService {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String cx;
    private final String endpoint;
    private final int numResults;

    public GoogleSearchService(String apiKey, String cx, String endpoint, int numResults) {
        this.apiKey = apiKey;
        this.cx = cx;
        this.endpoint = endpoint;
        this.numResults = numResults;
    }

    public List<SearchResponse.SearchResult> search(String query) {
        if (apiKey == null || apiKey.isBlank() || cx == null || cx.isBlank()) {
            log.warn("Google Search API key/cx not configured; returning empty.");
            return List.of();
        }
        String url = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("key", apiKey)
                .queryParam("cx", cx)
                .queryParam("q", query)
                .queryParam("num", numResults)
                .build().toUriString();
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = mapper.readTree(body);
            JsonNode items = root.path("items");
            List<SearchResponse.SearchResult> results = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode it : items) {
                    String title   = it.path("title").asText("");
                    String link    = it.path("link").asText("");
                    String snippet = it.path("snippet").asText("");
                    results.add(new SearchResponse.SearchResult(title, link, snippet, snippet));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Google Search API error: {}", e.getMessage());
            return List.of();
        }
    }
}
