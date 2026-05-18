package com.example.chatagent.service;

import com.example.chatagent.dto.ChatRequest;
import com.example.chatagent.model.github.GitHubProjectDocument;
import com.example.chatagent.model.github.RetrievalRouteDecision;
import com.example.chatagent.service.retrieval.GitHubDocumentRetrievalService;
import com.example.chatagent.service.retrieval.HybridRetrievalService;
import com.example.chatagent.service.retrieval.RetrievalRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
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
 * Orchestrates a single RAG turn:
 * <ol>
 *   <li>Retrieve top-K chunks from the pgvector KB.</li>
 *   <li>Emit progress events the front-end's StageToast can render.</li>
 *   <li>Stream the model answer token-by-token (or fall back to a context echo
 *       when no LLM credentials are configured).</li>
 *   <li>Send {@code answer_final} and complete the SSE stream.</li>
 * </ol>
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

    private final KnowledgeBaseService kb;
    private final SseEventWriter sse;
    private final ChatClient chatClient; // null when no LLM provider configured
    private final RetrievalRouterService router;
    private final GitHubDocumentRetrievalService githubRetrieval;
    private final HybridRetrievalService hybridRetrieval;

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
                       HybridRetrievalService hybridRetrieval) {
        this.kb = kb;
        this.sse = sse;
        this.router = router;
        this.githubRetrieval = githubRetrieval;
        this.hybridRetrieval = hybridRetrieval;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.defaultSystem(SYSTEM_PROMPT).build();
        if (this.chatClient == null) {
            log.warn("No ChatClient.Builder available — running in context-only fallback mode "
                    + "(set OPENAI_API_KEY to enable LLM responses).");
        }
    }

    /** Run the full pipeline asynchronously so the controller can return immediately. */
    @Async
    public void handle(ChatRequest req, SseEmitter emitter) {
        try {
            emit(emitter, "start", "Initialising",
                    Map.of("sessionId", req.sessionId() == null ? "" : req.sessionId()));

            // 1) Decide retrieval route
            RetrievalRouteDecision decision = router.decideRoute(req.safeMessage());
            emit(emitter, "retrieval_route_decided", "Routing retrieval",
                    Map.of("route", decision.getRoute(),
                           "reason", decision.getReason(),
                           "confidence", decision.getConfidence()));

            // 2) Retrieve according to the chosen route
            List<Document> kbHits = List.of();
            List<GitHubProjectDocument> githubHits = List.of();

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

            // 3) Generation
            emit(emitter, "generate", "Generating answer",
                    Map.of("toolName", "llm_generate", "status", "running"));
            String sid = req.sessionId() == null || req.sessionId().isBlank() ? "anonymous" : req.sessionId();
            String finalAnswer = streamAnswer(req.safeMessage(), context, sid, emitter);

            // 4) Done
            sse.sendAnswerFinal(emitter, finalAnswer);
            emit(emitter, "done", "Complete", Map.of("length", finalAnswer.length()));
            emitter.complete();
        } catch (Exception e) {
            log.error("Chat pipeline failed", e);
            sse.sendStage(emitter, "retrieve_error", "Knowledge base error",
                    Map.of("toolName", "kb_retrieve", "status", "failed", "error",
                           e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            sse.sendError(emitter, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            emitter.complete();
        }
    }

    /* ------------------------------------------------------------------ */

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
}
