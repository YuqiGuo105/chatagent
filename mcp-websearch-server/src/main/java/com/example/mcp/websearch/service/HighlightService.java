package com.example.mcp.websearch.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts keywords from a query (stopword filtering) and wraps matches in <mark> tags.
 */
@Service
public class HighlightService {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were",
            "of", "in", "on", "at", "to", "for", "with", "by", "from", "as",
            "how", "what", "why", "when", "where", "who", "which", "this", "that",
            "i", "you", "he", "she", "it", "we", "they", "be", "do", "does", "did"
    );

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    public List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) return List.of();
        Set<String> kws = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(query.toLowerCase());
        while (m.find()) {
            String t = m.group();
            if (t.length() >= 2 && !STOPWORDS.contains(t)) {
                kws.add(t);
            }
        }
        return new ArrayList<>(kws);
    }

    public String highlight(String text, List<String> keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty()) {
            return text;
        }
        String out = text;
        // sort by length desc to avoid nested matches
        List<String> sorted = new ArrayList<>(keywords);
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String kw : sorted) {
            String pattern = "(?i)(?<!<mark>)(" + Pattern.quote(kw) + ")(?!</mark>)";
            out = out.replaceAll(pattern, "<mark>$1</mark>");
        }
        return out;
    }
}
