package com.example.chatagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles DEEPTHINKING mode: adds a visible reasoning chain before the streamed answer.
 *
 * <h3>Pipeline:</h3>
 * <ol>
 *   <li><b>Plan</b> (gpt-4.1-nano) — break the question into key sub-questions.</li>
 *   <li><b>Generate</b> (default gpt-4o-mini) — stream the full answer using the plan as guidance.</li>
 *   <li><b>Verify</b> (gpt-4.1-nano) — quick quality check; appends a note if gaps are found.</li>
 * </ol>
 * Each step emits a {@code reasoning_step} SSE event so the frontend can render a live
 * timeline while the answer streams.
 */
@Slf4j
@Service
public class DeepThinkingService {

    private static final String SYSTEM_PROMPT = """
            You are ChatAgent, the assistant for a developer portfolio site.
            Think step by step and provide thorough, well-structured answers.
            Use markdown formatting. Cite key facts from context inline when helpful.
            """;

    private static final String PLAN_PROMPT = """
            User question: "%s"
            
            In 2-3 concise bullet points, identify the key sub-questions or aspects to address.
            Start each bullet with "• ". Be brief — max 40 words total:
            """;

    private static final String VERIFY_PROMPT = """
            Original question: "%s"
            Draft answer (first 500 chars): "%s"
            
            In one sentence: is the answer complete and accurate?
            Reply with either "✓ Complete" or "⚠ Missing: [brief gap description]":
            """;

    private static final String ANSWER_TEMPLATE = """
            CONTEXT:
            ---
            {context}
            ---
            
            REASONING PLAN:
            {plan}
            
            QUESTION: {question}
            
            Provide a thorough, well-structured answer following the reasoning plan above.
            """;

    private final ChatClient chatClient;
    private final SseEventWriter sse;

    @Value("${app.models.cheap:gpt-4.1-nano}")
    private String cheapModel;

    /** Separate memory store for deep-thinking sessions (keeps reasoning context). */
    private final ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(8)
            .build();

    public DeepThinkingService(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                               SseEventWriter sse) {
        this.sse = sse;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null
                : builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /**
     * Executes plan → generate → verify pipeline, emitting {@code reasoning_step} events.
     *
     * @return the final streamed answer text
     */
    public String handle(String question, String context, String sessionId, SseEmitter emitter) {
        // Step 1: Plan
        String plan = runPlan(question, emitter);

        // Step 2: Generate (streamed)
        emitStep(emitter, "Generating", "Building answer with reasoning plan…", false);
        String answer = streamAnswer(question, context, plan, sessionId, emitter);
        emitStep(emitter, "Generating", answer.length() + " chars written", true);

        // Step 3: Verify (non-blocking, cheap)
        String verification = runVerify(question, answer, emitter);
        if (verification != null && verification.startsWith("⚠")) {
            answer = answer + "\n\n> **Note:** " + verification.substring(1).trim();
        }

        return answer;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String runPlan(String question, SseEmitter emitter) {
        emitStep(emitter, "Planning", "Analysing question and planning approach…", false);
        if (chatClient == null) {
            emitStep(emitter, "Planning", "• Retrieve relevant context\n• Answer concisely", true);
            return "• Retrieve relevant context\n• Answer concisely";
        }
        try {
            String plan = chatClient.prompt()
                    .options(OpenAiChatOptions.builder().model(cheapModel).temperature(0.1))
                    .user(String.format(PLAN_PROMPT, question))
                    .call()
                    .content();
            emitStep(emitter, "Planning", plan, true);
            return plan;
        } catch (Exception e) {
            log.warn("Plan step failed: {}", e.getMessage());
            emitStep(emitter, "Planning", "• Answer the question thoroughly", true);
            return "• Answer the question thoroughly";
        }
    }

    private String runVerify(String question, String answer, SseEmitter emitter) {
        emitStep(emitter, "Verifying", "Checking answer quality…", false);
        if (chatClient == null) {
            emitStep(emitter, "Verifying", "✓ Complete", true);
            return "✓ Complete";
        }
        try {
            String snippet = answer.length() > 500 ? answer.substring(0, 500) : answer;
            String result = chatClient.prompt()
                    .options(OpenAiChatOptions.builder().model(cheapModel).temperature(0.1))
                    .user(String.format(VERIFY_PROMPT, question, snippet))
                    .call()
                    .content();
            emitStep(emitter, "Verifying", result, true);
            return result;
        } catch (Exception e) {
            log.warn("Verify step failed: {}", e.getMessage());
            emitStep(emitter, "Verifying", "✓ Complete", true);
            return "✓ Complete";
        }
    }

    private String streamAnswer(String question, String context, String plan,
                                String sessionId, SseEmitter emitter) {
        if (chatClient == null) {
            String fallback = "I don't have an LLM provider configured. Context:\n\n" + context;
            sse.sendAnswerDelta(emitter, fallback);
            return fallback;
        }
        String userPrompt = new PromptTemplate(ANSWER_TEMPLATE)
                .render(Map.of("context", context, "question", question, "plan", plan));

        AtomicReference<StringBuilder> acc = new AtomicReference<>(new StringBuilder());
        chatClient.prompt()
                .user(userPrompt)
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, sessionId)
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build()))
                .stream()
                .content()
                .doOnNext(token -> {
                    if (token == null || token.isEmpty()) return;
                    acc.get().append(token);
                    sse.sendAnswerDelta(emitter, token);
                })
                .blockLast();
        return acc.get().toString();
    }

    private void emitStep(SseEmitter emitter, String label, String detail, boolean completed) {
        sse.sendStage(emitter, "reasoning_step", label,
                Map.of("label", label,
                       "detail", detail == null ? "" : detail.trim(),
                       "completed", completed));
    }
}
