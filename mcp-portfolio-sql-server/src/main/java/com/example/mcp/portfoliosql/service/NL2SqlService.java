package com.example.mcp.portfoliosql.service;

import com.example.mcp.portfoliosql.config.PortfolioSqlProperties;
import com.example.mcp.portfoliosql.dto.ViewMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Natural-language -> SQL using OpenAI via Spring AI.
 *
 * Returns the raw SQL string; the SqlValidator is responsible for safety checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NL2SqlService {

    private final ChatClient.Builder chatClientBuilder;
    private final ViewMetadataCache metadataCache;
    private final PortfolioSqlProperties props;

    @Value("classpath:prompts/nl2sql-system.txt")
    private Resource systemPromptTemplate;

    private volatile ChatClient chatClient;
    private volatile String renderedSystemPrompt;

    public String generateSql(String question, String hintScope) {
        ensureInitialized();
        String userPrompt = hintScope == null || hintScope.isBlank()
                ? question
                : "(Scope hint: " + hintScope + ")\n" + question;

        String raw = chatClient.prompt()
                .system(renderedSystemPrompt)
                .user(userPrompt)
                .call()
                .content();

        return stripFencesAndSemicolons(raw);
    }

    private void ensureInitialized() {
        if (chatClient == null) {
            synchronized (this) {
                if (chatClient == null) {
                    this.chatClient = chatClientBuilder.build();
                    this.renderedSystemPrompt = renderSystemPrompt();
                }
            }
        }
    }

    private String renderSystemPrompt() {
        String tpl;
        try {
            tpl = new String(systemPromptTemplate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load nl2sql-system.txt", e);
        }
        return tpl
                .replace("{{schema}}", renderSchema())
                .replace("{{defaultLimit}}", String.valueOf(props.getExecution().getDefaultLimit()));
    }

    private String renderSchema() {
        return metadataCache.getAll().stream()
                .map(this::renderView)
                .collect(Collectors.joining("\n\n"));
    }

    private String renderView(ViewMetadata v) {
        String cols = v.columns().stream()
                .map(c -> "  - " + c.name() + " (" + c.type() + ")")
                .collect(Collectors.joining("\n"));
        String desc = v.description() == null ? "" : "\n  description: " + v.description().trim();
        return "VIEW " + v.viewName() + desc + "\n" + cols;
    }

    private static String stripFencesAndSemicolons(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
            t = t.trim();
        }
        while (t.endsWith(";")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}
