package com.example.chatagent.tool.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.stream.Collectors;

/**
 * Natural-language -> SQL using OpenAI via Spring AI.
 *
 * Returns the raw SQL string; {@link SqlValidator} is responsible for safety checks.
 *
 * <p>This class is NOT a Spring component — it is instantiated via
 * {@link com.example.chatagent.config.InProcessToolsConfig}. The raw system prompt
 * template string is loaded and passed in at construction time.</p>
 */
@Slf4j
public class NL2SqlService {

    private final ChatClient.Builder chatClientBuilder;
    private final ViewMetadataCache metadataCache;
    private final PortfolioSqlProperties props;
    private final String systemPromptTemplate;

    private volatile ChatClient chatClient;
    private volatile String renderedSystemPrompt;

    public NL2SqlService(ChatClient.Builder chatClientBuilder,
                         ViewMetadataCache metadataCache,
                         PortfolioSqlProperties props,
                         String systemPromptTemplate) {
        this.chatClientBuilder = chatClientBuilder;
        this.metadataCache = metadataCache;
        this.props = props;
        this.systemPromptTemplate = systemPromptTemplate;
    }

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
        return systemPromptTemplate
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
