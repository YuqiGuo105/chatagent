package com.example.chatagent.service;

import com.example.chatagent.dto.ChatRequest;
import com.example.chatagent.model.github.GitHubProjectDocument;
import com.example.chatagent.model.github.RetrievalRouteDecision;
import com.example.chatagent.service.retrieval.GitHubDocumentRetrievalService;
import com.example.chatagent.service.retrieval.HybridRetrievalService;
import com.example.chatagent.service.retrieval.RetrievalRouterService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Orchestrates RAG turns for all chat modes:
 * <ul>
 *   <li><b>FAST</b> (default) — standard RAG pipeline.</li>
 *   <li><b>DEEPTHINKING</b> — adds plan → generate → verify reasoning chain via {@link DeepThinkingService}.</li>
 *   <li><b>WEB_GUIDE</b> — generates a personalised site tour via {@link WebGuideService}.</li>
 *   <li><b>ENHANCE</b> — standard RAG + LLM key-concept extraction emitted as {@code key_concepts}.</li>
 * </ul>
 */
@Slf4j
@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            You are ChatAgent, the assistant for a developer portfolio site.
            Answer the user's question using ONLY the information in the
            CONTEXT block when relevant. If the context is insufficient,
            say so briefly and answer from general knowledge.
            Be concise, use markdown, and cite key facts inline when helpful.
            """;

    private static final String USER_TEMPLATE = """
            CONTEXT:
            ---
            {context}
            ---

            QUESTION: {question}
            """;

    private static final String CONCEPTS_PROMPT = """
            Text (max 600 chars): "%s"
            
            Extract up to 8 key concepts. For each return:
            {"term":"...","type":"TECH|PERSON|ORG|CONCEPT","importance":"primary|secondary|contextual"}
            
            Return ONLY: {"concepts":[...]}
            """;

    private final KnowledgeBaseService kb;
    private final SseEventWriter sse;
    private final ChatClient chatClient;
    private final RetrievalRouterService router;
    private final GitHubDocumentRetrievalService githubRetrieval;
    private final HybridRetrievalService hybridRetrieval;
    private final WebGuideService webGuideService;
    private final DeepThinkingService deepThinkingService;
    private final ObjectMapper objectMapper;

    @Value("${app.models.cheap:gpt-4.1-nano}")
    private String cheapModel;

    /** In-memory rolling conversation window, keyed by sessionId. */
    private final ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(10)
            .build();

    public ChatService(KnowledgeBaseService kb,
                       SseEventWriter sse,
                       ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                       RetrievalRouterService router,
                       GitHubDocumentRetrievalService githubRetrieval,
                       HybridRetrievalService hybridRetrieval,
                       WebGuideService webGuideService,
                       DeepThinkingService deepThinkingService,
                       ObjectMapper objectMapper) {
        this.kb = kb;
        this.sse = sse;
        this.router = router;
        this.githubRetrieval = githubRetrieval;
        this.hybridRetrieval = hybridRetrieval;
        this.webGuideService = webGuideService;
        this.deepThinkingService = deepThinkingService;
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.defaultSystem(SYSTEM_PROMPT).build();
        if (this.chatClient == null) {
            log.warn("No ChatClient.Builder available — running in context-only fallback mode "
                    + "(set OPENAI_API_KEY to enable LLM responses).");
        }
    }

    /** Dispatch to the appropriate mode pipeline asynchronously. */
    @Async
    public void handle(ChatRequest req, SseEmitter emitter) {
        if (req.isWebGuide()) {
            webGuideService.handleWebGuide(req.safeMessage(), emitter);
            return;
        }
        if (req.isDeepThinking()) {
            runDeepThinkingPipeline(req, emitter);
            return;
        }
        if (req.isEnhance()) {
            runEnhancePipeline(req, emitter);
            return;
        }
        // Default: FAST mode
        runDefaultPipeline(req, emitter);
    }

    /* ======================================================
       FAST (default) pipeline
       ====================================================== */

    private void runDefaultPipeline(ChatRequest req, SseEmitter emitter) {
        try {
            emit(emitter, "start", "Initialising",
                    Map.of("sessionId", req.sessionId() == null ? "" : req.sessionId()));

            List<Document> kbHits = List.of();
            List<GitHubProjectDocument> githubHits = List.of();

            RetrievalRouteDecision decision = router.decideRoute(req.safeMessage());
            emit(emitter, "retrieval_route_decided", "Routing retrieval",
                    Map.of("route", decision.getRoute(),
                           "reason", decision.getReason(),
                           "confidence", decision.getConfidence()));

            switch (decision.getRoute()) {
                case RetrievalRouteDecision.ROUTE_GITHUB_CODE ->
                        githubHits = runGitHubSearch(req.safeMessage(), "code", emitter);
                case RetrievalRouteDecision.ROUTE_GITHUB_CONTENT ->
                        githubHits = runGitHubSearch(req.safeMessage(), "document", emitter);
                case RetrievalRouteDecision.ROUTE_HYBRID -> {
                    kbHits = runKbSearch(req.safeMessage(), emitter);
                    githubHits = runGitHubSearch(req.safeMessage(), null, emitter);
                }
                default -> kbHits = runKbSearch(req.safeMessage(), emitter);
            }

            String context = buildCombinedContext(kbHits, githubHits);

            emit(emitter, "generate", "Generating answer",
                    Map.of("toolName", "llm_generate", "status", "running"));
            String sid = req.sessionId() == null || req.sessionId().isBlank() ? "anonymous" : req.sessionId();
            String finalAnswer = streamAnswer(req.safeMessage(), context, sid, emitter);

            sse.sendAnswerFinal(emitter, finalAnswer);
            emit(emitter, "done", "Complete", Map.of("length", finalAnswer.length()));
            emitter.complete();
        } catch (Exception e) {
            handleError(emitter, e);
        }
    }

    /* ======================================================
       DEEPTHINKING pipeline
       ====================================================== */

    private void runDeepThinkingPipeline(ChatRequest req, SseEmitter emitter) {
        try {
            emit(emitter, "start", "Initialising deep thinking",
                    Map.of("sessionId", req.sessionId() == null ? "" : req.sessionId()));

            // Retrieve context (always use hybrid for deep thinking — more thorough)
            List<Document> kbHits = runKbSearch(req.safeMessage(), emitter);
            List<GitHubProjectDocument> githubHits = List.of();
            try {
                githubHits = runGitHubSearch(req.safeMessage(), null, emitter);
            } catch (Exception ignore) {
                log.debug("GitHub search skipped in deep thinking: {}", ignore.getMessage());
            }
            String context = buildCombinedContext(kbHits, githubHits);

            emit(emitter, "generate", "Deep reasoning started",
                    Map.of("toolName", "llm_deep_think", "status", "running"));

            String sid = req.sessionId() == null || req.sessionId().isBlank() ? "anonymous" : req.sessionId();
            String finalAnswer = deepThinkingService.handle(req.safeMessage(), context, sid, emitter);

            sse.sendAnswerFinal(emitter, finalAnswer);
            emit(emitter, "done", "Complete", Map.of("length", finalAnswer.length()));
            emitter.complete();
        } catch (Exception e) {
            handleError(emitter, e);
        }
    }

    /* ======================================================
       ENHANCE pipeline
       ====================================================== */

    private void runEnhancePipeline(ChatRequest req, SseEmitter emitter) {
        try {
            emit(emitter, "start", "Initialising enhanced mode",
                    Map.of("sessionId", req.sessionId() == null ? "" : req.sessionId()));

            // 1. Standard retrieval
            List<Document> kbHits = List.of();
            List<GitHubProjectDocument> githubHits = List.of();
            RetrievalRouteDecision decision = router.decideRoute(req.safeMessage());
            emit(emitter, "retrieval_route_decided", "Routing retrieval",
                    Map.of("route", decision.getRoute(), "reason", decision.getReason(),
                           "confidence", decision.getConfidence()));
            switch (decision.getRoute()) {
                case RetrievalRouteDecision.ROUTE_GITHUB_CODE ->
                        githubHits = runGitHubSearch(req.safeMessage(), "code", emitter);
                case RetrievalRouteDecision.ROUTE_GITHUB_CONTENT ->
                        githubHits = runGitHubSearch(req.safeMessage(), "document", emitter);
                case RetrievalRouteDecision.ROUTE_HYBRID -> {
                    kbHits = runKbSearch(req.safeMessage(), emitter);
                    githubHits = runGitHubSearch(req.safeMessage(), null, emitter);
                }
                default -> kbHits = runKbSearch(req.safeMessage(), emitter);
            }
            String context = buildCombinedContext(kbHits, githubHits);

            // 2. Generate answer
            emit(emitter, "generate", "Generating answer",
                    Map.of("toolName", "llm_generate", "status", "running"));
            String sid = req.sessionId() == null || req.sessionId().isBlank() ? "anonymous" : req.sessionId();
            String finalAnswer = streamAnswer(req.safeMessage(), context, sid, emitter);

            // 3. Extract key concepts (cheap, non-blocking from user's perspective)
            extractAndEmitKeyConcepts(finalAnswer, emitter);

            sse.sendAnswerFinal(emitter, finalAnswer);
            emit(emitter, "done", "Complete", Map.of("length", finalAnswer.length()));
            emitter.complete();
        } catch (Exception e) {
            handleError(emitter, e);
        }
    }

    private void extractAndEmitKeyConcepts(String answer, SseEmitter emitter) {
        if (chatClient == null) return;
        try {
            String snippet = answer.length() > 600 ? answer.substring(0, 600) : answer;
            String raw = chatClient.prompt()
                    .options(OpenAiChatOptions.builder().model(cheapModel).temperature(0.1))
                    .user(String.format(CONCEPTS_PROMPT, snippet))
                    .call()
                    .content();

            // Parse and emit
            String json = raw == null ? "" : raw.trim();
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
                Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
                Object concepts = parsed.get("concepts");
                if (concepts != null) {
                    sse.sendStage(emitter, "key_concepts", "Key concepts extracted",
                            Map.of("concepts", concepts));
                }
            }
        } catch (Exception e) {
            log.debug("Key concept extraction failed (non-fatal): {}", e.getMessage());
        }
    }

    private List<Document> runKbSearch(String question, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        emit(emitter, "retrieve", "Searching knowledge base",
                Map.of("toolName", "kb_retrieve", "status", "running", "query", question));
        List<Document> hits = kb.search(question);
        long ms = System.currentTimeMillis() - start;
        emit(emitter, "docsFound", "Found " + hits.size() + " relevant chunks",
                Map.of("toolName", "kb_retrieve", "status", "completed",
                       "documentCount", hits.size(), "chunksFound", hits.size(),
                       "latency", ms));
        return hits;
    }

    private List<GitHubProjectDocument> runGitHubSearch(String question, String fileType, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        Map<String, Object> startPayload = new LinkedHashMap<>();
        startPayload.put("toolName", "github_project_search");
        startPayload.put("status", "running");
        if (fileType != null) startPayload.put("searchType", fileType);
        startPayload.put("query", question);
        emit(emitter, "github_search_start", "Searching GitHub project content", startPayload);

        List<GitHubProjectDocument> hits = fileType == null
                ? githubRetrieval.retrieve(question)
                : githubRetrieval.retrieve(question, fileType);
        long ms = System.currentTimeMillis() - start;

        if (hits.isEmpty()) {
            emit(emitter, "github_search_empty", "No GitHub matches",
                    Map.of("toolName", "github_project_search", "status", "completed",
                           "resultCount", 0, "latency", ms));
            return hits;
        }
        emit(emitter, "github_search_done", "Found " + hits.size() + " GitHub snippets",
                Map.of("toolName", "github_project_search", "status", "completed",
                       "resultCount", hits.size(), "latency", ms));
        emit(emitter, "github_sources_selected", "Selected GitHub source files",
                Map.of("sources", extractSourceInfo(hits)));
        return hits;
    }

    private List<Map<String, Object>> extractSourceInfo(List<GitHubProjectDocument> hits) {
        List<Map<String, Object>> out = new ArrayList<>(hits.size());
        for (GitHubProjectDocument h : hits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("repo", h.repoSlug());
            m.put("filePath", h.getFilePath());
            m.put("category", h.getFileType());
            m.put("chunkIndex", h.getChunkIndex());
            m.put("similarity", h.getSimilarity());
            out.add(m);
        }
        return out;
    }

    private String buildCombinedContext(List<Document> kbHits, List<GitHubProjectDocument> githubHits) {
        StringBuilder sb = new StringBuilder();
        if (kbHits != null && !kbHits.isEmpty()) {
            sb.append("# Knowledge base\n");
            sb.append(kbHits.stream()
                    .map(d -> "- " + d.getText().replace("\n", " ").trim())
                    .collect(Collectors.joining("\n")));
            sb.append('\n');
        }
        if (githubHits != null && !githubHits.isEmpty()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("# GitHub project content\n");
            for (GitHubProjectDocument h : githubHits) {
                sb.append("## ").append(h.repoSlug()).append(" — ").append(h.getFilePath());
                if (h.getChunkIndex() != null) sb.append(" (chunk ").append(h.getChunkIndex()).append(')');
                sb.append('\n');
                sb.append("```\n").append(h.getContent()).append("\n```\n");
            }
        }
        if (sb.length() == 0) return "(no matching documents)";
        return sb.toString();
    }

    private String streamAnswer(String question, String context, String sessionId, SseEmitter emitter) {
        if (chatClient == null) {
            String fallback = "I don't have an LLM provider configured right now. "
                    + "Here is the most relevant context I retrieved:\n\n" + context;
            // Emit it as a single chunk so the UI animates consistently.
            sse.sendAnswerDelta(emitter, fallback);
            return fallback;
        }

        String userPrompt = new PromptTemplate(USER_TEMPLATE).render(
                Map.of("context", context, "question", question));

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

    private void emit(SseEmitter emitter, String stage, String message, Map<String, Object> payload) {
        Map<String, Object> map = new LinkedHashMap<>(payload);
        sse.sendStage(emitter, stage, message, map);
    }

    private void handleError(SseEmitter emitter, Exception e) {
        log.error("Chat pipeline failed", e);
        sse.sendStage(emitter, "retrieve_error", "Pipeline error",
                Map.of("toolName", "pipeline", "status", "failed", "error",
                       e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        sse.sendError(emitter, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        emitter.complete();
    }
}
