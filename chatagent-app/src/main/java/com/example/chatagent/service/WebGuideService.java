package com.example.chatagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/**
 * Generates dynamic, personalised site-tour steps using gpt-4.1-nano (cheapest model).
 *
 * <p>The LLM selects and annotates 3–5 sections from the portfolio that are most
 * relevant to the user's stated interest, then emits a {@code tour_steps} SSE event
 * containing the step array so the frontend SiteTour can start a tailored walkthrough.</p>
 */
@Slf4j
@Service
public class WebGuideService {

    private static final String SECTIONS_DESC = """
            Available page sections — use these targetId values EXACTLY:
            - tour-about        : "About Me" section
            - tour-background   : Background, education, work experience
            - tour-projects     : Projects and shipped products
            - tour-techblogs    : Technical blog posts
            - tour-life         : Life, hobbies, personal interests
            - tour-real-time-data : Live market data, currency, weather
            - tour-contact      : Contact information
            """;

    private static final String PROMPT_TEMPLATE = """
            You are a helpful web guide generator for a developer portfolio.
            The user is interested in: "%s"
            
            %s
            
            Select 3-5 sections most relevant to the user's interest and generate a personalised tour.
            Return ONLY a valid JSON array. Each element must have exactly these keys:
            - "id": a short unique slug (e.g. "projects")
            - "targetId": one of the exact targetId values listed above
            - "title": short section name
            - "content": 1-2 sentence description tailored to the user's interest
            
            Example:
            [{"id":"projects","targetId":"tour-projects","title":"My Projects","content":"Here you will find..."}]
            
            Output the JSON array only, with no extra text:
            """;

    private static final List<Map<String, Object>> DEFAULT_STEPS = List.of(
            Map.of("id", "about",    "targetId", "tour-about",    "title", "About Me",     "content", "Start with a quick snapshot of who Yuqi is."),
            Map.of("id", "projects", "targetId", "tour-projects", "title", "My Projects",  "content", "Browse the flagship projects Yuqi has shipped."),
            Map.of("id", "contact",  "targetId", "tour-contact",  "title", "Contact Me",   "content", "Reach out to collaborate or connect.")
    );

    private static final Set<String> VALID_TARGET_IDS = Set.of(
            "tour-about", "tour-background", "tour-projects",
            "tour-techblogs", "tour-life", "tour-real-time-data", "tour-contact"
    );

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final SseEventWriter sse;

    @Value("${app.models.cheap:gpt-4.1-nano}")
    private String cheapModel;

    public WebGuideService(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                           ObjectMapper objectMapper,
                           SseEventWriter sse) {
        this.objectMapper = objectMapper;
        this.sse = sse;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
    }

    /**
     * Runs the full web-guide pipeline for a given user intent:
     * <ol>
     *   <li>Calls gpt-4.1-nano to select and annotate relevant sections.</li>
     *   <li>Emits a {@code tour_steps} SSE event with the step array.</li>
     *   <li>Emits {@code answer_final} with a short human-readable confirmation.</li>
     *   <li>Completes the emitter.</li>
     * </ol>
     */
    public void handleWebGuide(String userIntent, SseEmitter emitter) {
        try {
            sse.sendStage(emitter, "web_guide_start", "Personalising your tour",
                    Map.of("toolName", "web_guide", "status", "running"));

            List<Map<String, Object>> steps = generateSteps(userIntent);

            sse.sendStage(emitter, "tour_steps", "Tour ready",
                    Map.of("steps", steps, "autoStart", true, "count", steps.size()));

            String answer = "I've prepared a personalised tour of **" + steps.size() +
                    " sections** based on your interest. The guided tour is starting now — " +
                    "use **Next / Prev** to navigate each section!";
            sse.sendAnswerFinal(emitter, answer);
            sse.sendStage(emitter, "done", "Complete", Map.of("length", answer.length()));
            emitter.complete();
        } catch (Exception e) {
            log.error("WebGuide pipeline failed", e);
            sse.sendError(emitter, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            emitter.complete();
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private List<Map<String, Object>> generateSteps(String userIntent) {
        if (chatClient == null) {
            return new ArrayList<>(DEFAULT_STEPS);
        }
        try {
            String prompt = String.format(PROMPT_TEMPLATE, userIntent, SECTIONS_DESC);
            String raw = chatClient.prompt()
                    .options(OpenAiChatOptions.builder().model(cheapModel).temperature(0.2))
                    .user(prompt)
                    .call()
                    .content();
            return parseJsonSteps(raw);
        } catch (Exception e) {
            log.warn("WebGuide step generation failed, using defaults: {}", e.getMessage());
            return new ArrayList<>(DEFAULT_STEPS);
        }
    }

    private List<Map<String, Object>> parseJsonSteps(String raw) {
        try {
            String json = raw == null ? "" : raw.trim();
            int start = json.indexOf('[');
            int end   = json.lastIndexOf(']');
            if (start < 0 || end <= start) return new ArrayList<>(DEFAULT_STEPS);
            json = json.substring(start, end + 1);

            List<Map<String, Object>> steps = objectMapper.readValue(json, new TypeReference<>() {});
            // Validate that every step references a known DOM section
            steps.removeIf(s -> !VALID_TARGET_IDS.contains(s.get("targetId")));
            return steps.isEmpty() ? new ArrayList<>(DEFAULT_STEPS) : steps;
        } catch (Exception e) {
            log.warn("Failed to parse tour steps JSON: {}", e.getMessage());
            return new ArrayList<>(DEFAULT_STEPS);
        }
    }
}
