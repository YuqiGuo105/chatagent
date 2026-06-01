package com.example.chatagent.tool.sitetour;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-process MCP tool that generates a personalised portfolio site tour.
 *
 * <p>Migrated from {@code WebGuideService} and exposed as a standard {@code @Tool}
 * so the LLM can call it autonomously in any mode (FAST / DEEPTHINKING)
 * instead of being hard-wired to the now-removed WEB_GUIDE mode.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteTourTool {

    private static final String SECTIONS_DESC = """
            Available page sections — use these targetId values EXACTLY:
            - tour-about         : "About Me" section
            - tour-background    : Background, education, work experience
            - tour-projects      : Projects and shipped products
            - tour-techblogs     : Technical blog posts
            - tour-life          : Life, hobbies, personal interests
            - tour-real-time-data: Live market data, currency, weather
            - tour-contact       : Contact information
            """;

    private static final String PROMPT_TEMPLATE = """
            You are a helpful web guide generator for a developer portfolio.
            The user is interested in: "%s"
            
            %s
            
            Select 3-5 sections most relevant to the user's interest and generate a personalised tour.
            Return ONLY a valid JSON array. Each element must have exactly these keys:
            - "id":       a short unique slug (e.g. "projects")
            - "targetId": one of the exact targetId values listed above
            - "title":    short section name
            - "content":  1-2 sentence description tailored to the user's interest
            
            Output the JSON array only, no extra text:
            """;

    private static final List<Map<String, Object>> DEFAULT_STEPS = List.of(
            Map.of("id", "about",    "targetId", "tour-about",    "title", "About Me",    "content", "Start with a quick snapshot of who Yuqi is."),
            Map.of("id", "projects", "targetId", "tour-projects", "title", "My Projects", "content", "Browse the flagship projects Yuqi has shipped."),
            Map.of("id", "contact",  "targetId", "tour-contact",  "title", "Contact Me",  "content", "Reach out to collaborate or connect.")
    );

    private static final Set<String> VALID_TARGET_IDS = Set.of(
            "tour-about", "tour-background", "tour-projects",
            "tour-techblogs", "tour-life", "tour-real-time-data", "tour-contact");

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.models.cheap:gpt-4.1-nano}")
    private String cheapModel;

    @Tool(name = "generate_site_tour",
          description = """
                  Generate a personalised, step-by-step guided tour of the portfolio website \
                  based on the user's stated interest or intent.

                  Use this tool when the user asks to be shown around the site, wants a tour, \
                  a walkthrough, or says "show me your portfolio" / "导览" / "带我看".

                  Returns a list of tour steps (id, targetId, title, content) that the frontend \
                  SiteTour widget will use to highlight and scroll to each section.
                  Each targetId maps to a DOM anchor in the portfolio page.
                  """)
    public SiteTourResult generateSiteTour(
            @ToolParam(description = "The user's interest or intent, e.g. 'AI projects', 'web dev background'")
            String userIntent
    ) {
        log.info("generate_site_tour called: intent='{}'", userIntent);
        List<Map<String, Object>> steps = buildSteps(userIntent);
        String summary = "I've prepared a personalised tour of **" + steps.size() +
                " sections** based on your interest. The guided tour is starting — " +
                "use **Next / Prev** to navigate!";
        return new SiteTourResult(steps, summary);
    }

    private List<Map<String, Object>> buildSteps(String userIntent) {
        try {
            String prompt = String.format(PROMPT_TEMPLATE, userIntent, SECTIONS_DESC);
            String raw = chatClientBuilder.build().prompt()
                    .options(OpenAiChatOptions.builder().model(cheapModel).temperature(0.2))
                    .user(prompt)
                    .call()
                    .content();
            return parseSteps(raw);
        } catch (Exception e) {
            log.warn("SiteTour step generation failed, using defaults: {}", e.getMessage());
            return new ArrayList<>(DEFAULT_STEPS);
        }
    }

    private List<Map<String, Object>> parseSteps(String raw) {
        try {
            String json = raw == null ? "" : raw.trim();
            int s = json.indexOf('['), e = json.lastIndexOf(']');
            if (s < 0 || e <= s) return new ArrayList<>(DEFAULT_STEPS);
            List<Map<String, Object>> steps = objectMapper.readValue(json.substring(s, e + 1), new TypeReference<>() {});
            steps.removeIf(step -> !VALID_TARGET_IDS.contains(step.get("targetId")));
            return steps.isEmpty() ? new ArrayList<>(DEFAULT_STEPS) : steps;
        } catch (Exception e) {
            log.warn("Failed to parse tour steps: {}", e.getMessage());
            return new ArrayList<>(DEFAULT_STEPS);
        }
    }

    public record SiteTourResult(List<Map<String, Object>> steps, String summary) {}
}
