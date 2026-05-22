package com.example.mcp.websearch.service;

import com.example.mcp.websearch.dto.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around Google Custom Search JSON API.
 */
@Slf4j
@Service
public class GoogleSearchService {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${google.search.api-key:}")
    private String apiKey;

    @Value("${google.search.cx:}")
    private String cx;

    @Value("${google.search.endpoint:https://www.googleapis.com/customsearch/v1}")
    private String endpoint;

    @Value("${google.search.num-results:10}")
    private int numResults;

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
                    String title = it.path("title").asText("");
                    String link = it.path("link").asText("");
                    String snippet = it.path("snippet").asText("");
                    results.add(new SearchResponse.SearchResult(title, link, snippet, snippet));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Google search failed", e);
            return List.of();
        }
    }
}
