package com.example.chatagent.tool.concepts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * In-process tool that extracts key concepts from text.
 *
 * <p>Migrated from {@code ChatService.extractAndEmitKeyConcepts()} and exposed as a
 * standard {@code @Tool} so the LLM can call it autonomously instead of being
 * forced in every ENHANCE mode response.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyConceptExtractorTool {

    private static final String PROMPT_TEMPLATE = """
            Text (max 600 chars): "%s"
            
            Extract up to 8 key concepts. For each return:
            {"term":"...","type":"TECH|PERSON|ORG|CONCEPT","importance":"primary|secondary|contextual"}
            
            Return ONLY: {"concepts":[...]}
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.models.cheap:gpt-4.1-nano}")
    private String cheapModel;

    @Tool(name = "extract_key_concepts",
          description = """
                  Extract and classify the key concepts, technologies, organisations, and \
                  people mentioned in a piece of text.

                  Use this tool when the user asks to "summarise key terms", "list technologies", \
                  "extract concepts", or when producing a deep explanation that benefits from \
                  a structured concept breakdown.

                  Returns a list of up to 8 tagged concepts with: term, type \
                  (TECH|PERSON|ORG|CONCEPT), and importance (primary|secondary|contextual).
                  """)
    public ConceptResult extractKeyConcepts(
            @ToolParam(description = "The text to analyse. Will be truncated to 600 chars internally.")
            String text
    ) {
        log.info("extract_key_concepts called: {} chars", text == null ? 0 : text.length());
        if (text == null || text.isBlank()) return new ConceptResult(List.of());
        String snippet = text.length() > 600 ? text.substring(0, 600) : text;
        try {
            String raw = chatClientBuilder.build().prompt()
                    .options(OpenAiChatOptions.builder().model(cheapModel).temperature(0.1))
                    .user(String.format(PROMPT_TEMPLATE, snippet))
                    .call()
                    .content();
            return parse(raw);
        } catch (Exception e) {
            log.warn("Key concept extraction failed: {}", e.getMessage());
            return new ConceptResult(List.of());
        }
    }

    private ConceptResult parse(String raw) {
        try {
            String json = raw == null ? "" : raw.trim();
            int s = json.indexOf('{'), e = json.lastIndexOf('}');
            if (s < 0 || e <= s) return new ConceptResult(List.of());
            Map<String, Object> parsed = objectMapper.readValue(json.substring(s, e + 1), new TypeReference<>() {});
            Object concepts = parsed.get("concepts");
            if (concepts instanceof List<?> list) {
                return new ConceptResult(list.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> (Map<String, Object>) m)
                        .toList());
            }
        } catch (Exception e) {
            log.debug("Concept JSON parse error: {}", e.getMessage());
        }
        return new ConceptResult(List.of());
    }

    public record ConceptResult(List<Map<String, Object>> concepts) {}
}
