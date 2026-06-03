package com.example.chatagent.service;

import com.example.chatagent.dto.ChatRequest;
import com.example.chatagent.dto.SourceCardDto;
import com.example.chatagent.memory.ToolCallRecordingAdvisor;
import com.example.chatagent.service.intent.IntentRouter;
import com.example.chatagent.service.intent.IntentRouter.IntentDecision;
import com.example.chatagent.tool.analytics.VisitorAnalyticsTool;
import com.example.chatagent.tool.concepts.KeyConceptExtractorTool;
import com.example.chatagent.tool.portfolio.PortfolioSqlTool;
import com.example.chatagent.tool.sitetour.SiteTourTool;
import com.example.chatagent.tool.webops.WebOpsTool;
import com.example.chatagent.tool.websearch.WebSearchTool;
import com.example.chatagent.model.github.GitHubProjectDocument;
import com.example.chatagent.model.github.RetrievalRouteDecision;
import com.example.chatagent.service.retrieval.GitHubDocumentRetrievalService;
import com.example.chatagent.service.retrieval.HybridRetrievalService;
import com.example.chatagent.service.retrieval.RetrievalRouterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
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

/**
 * Orchestrates RAG turns for all chat modes:
 * <ul>
 *   <li><b>FAST</b> (default) — intent-driven pipeline: {@link IntentRouter} decides whether
 *       RAG is needed; MCP tools (including {@code query_portfolio_database},
 *       {@code generate_site_tour}, {@code extract_key_concepts}) are available for autonomous
 *       LLM invocation.</li>
 *   <li><b>DEEPTHINKING</b> — adds plan → generate → verify reasoning chain via
 *       {@link DeepThinkingService} with hybrid RAG.</li>
 * </ul>
 *
 * <p>The former WEB_GUIDE and ENHANCE modes have been removed as dedicated pipelines.
 * Their capabilities are now provided by {@code generate_site_tour} and
 * {@code extract_key_concepts} tools which the LLM can call autonomously.</p>
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

    /** Only this email is allowed to perform blog write operations. */
    private static final String BLOG_OWNER_EMAIL = "yuqi.guo17@gmail.com";

    /**
     * Builds the full system prompt for this request, appending an AUTH CONTEXT block
     * so the LLM knows whether blog write operations are permitted, and optional
     * TOOL GUIDANCE when the intent router hints at a specific tool.
     */
    private static String buildSystemPrompt(String userEmail, List<String> toolHints) {
        boolean isOwner = BLOG_OWNER_EMAIL.equalsIgnoreCase(userEmail);
        String authBlock = isOwner
                ? "\n\nAUTH CONTEXT: User is authenticated as " + userEmail + " (site owner).\n"
                  + "Blog write operations (create_tech_blog, update_tech_blog, delete_tech_blog,\n"
                  + "create_life_blog, update_life_blog, delete_life_blog) are PERMITTED."
                : "\n\nAUTH CONTEXT: "
                  + (userEmail == null || userEmail.isBlank()
                        ? "No authenticated user."
                        : "User email '" + userEmail + "' is not authorised for blog management.")
                  + "\nBlog write operations (create_tech_blog, update_tech_blog, delete_tech_blog,\n"
                  + "create_life_blog, update_life_blog, delete_life_blog) are NOT PERMITTED.\n"
                  + "If the user asks to create, update, or delete blog posts, politely refuse and\n"
                  + "tell them they must log in as the site owner first.";
        String toolBlock = "";
        if (toolHints != null && !toolHints.isEmpty()) {
            toolBlock = "\n\nTOOL GUIDANCE: For this question, you MUST call the following "
                    + "tool(s) to answer accurately: " + String.join(", ", toolHints) + "."
                    + "\nDo not answer from general knowledge alone — use the tool to retrieve "
                    + "real data and then present it to the user.";
        }
        return SYSTEM_PROMPT + authBlock + toolBlock;
    }

    private static final String USER_TEMPLATE = """
            CONTEXT:
            ---
            {context}
            ---

            QUESTION: {question}
            """;

    private final KnowledgeBaseService kb;
    private final SseEventWriter sse;
    private final ChatClient chatClient;
    private final RetrievalRouterService router;
    private final GitHubDocumentRetrievalService githubRetrieval;
    private final HybridRetrievalService hybridRetrieval;
    private final DeepThinkingService deepThinkingService;
    private final IntentRouter intentRouter;
    private final ToolCallRecordingAdvisor toolCallRecordingAdvisor;
    private final SourceEnrichmentService sourceEnrichment;

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
                       DeepThinkingService deepThinkingService,
                       IntentRouter intentRouter,
                       ToolCallRecordingAdvisor toolCallRecordingAdvisor,
                       SourceEnrichmentService sourceEnrichment,
                       ObjectProvider<org.springframework.ai.tool.ToolCallbackProvider> mcpToolsProvider,
                       ObjectProvider<VisitorAnalyticsTool> visitorAnalyticsToolProvider,
                       ObjectProvider<WebOpsTool> webOpsToolProvider,
                       ObjectProvider<WebSearchTool> webSearchToolProvider,
                       ObjectProvider<SiteTourTool> siteTourToolProvider,
                       ObjectProvider<KeyConceptExtractorTool> keyConceptExtractorToolProvider,
                       ObjectProvider<PortfolioSqlTool> portfolioSqlToolProvider) {
        this.kb = kb;
        this.sse = sse;
        this.router = router;
        this.githubRetrieval = githubRetrieval;
        this.hybridRetrieval = hybridRetrieval;
        this.deepThinkingService = deepThinkingService;
        this.intentRouter = intentRouter;
        this.toolCallRecordingAdvisor = toolCallRecordingAdvisor;
        this.sourceEnrichment = sourceEnrichment;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder != null) {
            builder = builder.defaultSystem(SYSTEM_PROMPT);
            // Remote MCP tool callbacks (when MCP servers are deployed)
            org.springframework.ai.tool.ToolCallbackProvider mcp = mcpToolsProvider.getIfAvailable();
            if (mcp != null) {
                builder = builder.defaultToolCallbacks(mcp);
                log.info("ChatClient wired with MCP tool callbacks.");
            }
            // In-process tool beans
            java.util.List<Object> inProcess = new java.util.ArrayList<>();
            visitorAnalyticsToolProvider.ifAvailable(inProcess::add);
            webOpsToolProvider.ifAvailable(inProcess::add);
            webSearchToolProvider.ifAvailable(inProcess::add);
            siteTourToolProvider.ifAvailable(inProcess::add);
            keyConceptExtractorToolProvider.ifAvailable(inProcess::add);
            portfolioSqlToolProvider.ifAvailable(inProcess::add);
            if (!inProcess.isEmpty()) {
                builder = builder.defaultTools(inProcess.toArray());
                log.info("ChatClient wired with {} in-process tool bean(s): {}",
                        inProcess.size(),
                        inProcess.stream().map(t -> t.getClass().getSimpleName()).toList());
            }
            this.chatClient = builder.build();
        } else {
            this.chatClient = null;
            log.warn("No ChatClient.Builder available - running in context-only fallback mode "
                    + "(set OPENAI_API_KEY to enable LLM responses).");
        }
    }

    /** Dispatch to the appropriate mode pipeline asynchronously. */
    @Async
    public void handle(ChatRequest req, SseEmitter emitter) {
        if (req.isDeepThinking()) {
            runDeepThinkingPipeline(req, emitter);
            return;
        }
        // Default: FAST mode (handles legacy WEB_GUIDE / ENHANCE modes too)
        runFastPipeline(req, emitter);
    }

    /* ======================================================
       FAST (default) pipeline — intent-driven, RAG optional
       ====================================================== */

    private void runFastPipeline(ChatRequest req, SseEmitter emitter) {
        try {
            emit(emitter, "start", "Initialising",
                    Map.of("sessionId", req.sessionId() == null ? "" : req.sessionId()));

            // Route intent
            IntentDecision intent = intentRouter.decide(req);
            log.info("IntentDecision: useRAG={} scope={} tools={} reason='{}'",
                    intent.useRAG(), intent.ragScope(), intent.toolHints(), intent.reasoning());
            emit(emitter, "intent_decided", "Intent routed",
                    Map.of("useRAG", intent.useRAG(),
                           "ragScope", intent.ragScope(),
                           "toolHints", intent.toolHints(),
                           "reasoning", intent.reasoning()));

            List<Document> kbHits = List.of();
            List<GitHubProjectDocument> githubHits = List.of();

            if (intent.useRAG()) {
                String scope = intent.ragScope();
                if ("KB_ONLY".equals(scope)) {
                    kbHits = runKbSearch(req.safeMessage(), emitter);
                } else if ("GITHUB_ONLY".equals(scope)) {
                    githubHits = runGitHubSearch(req.safeMessage(), null, emitter);
                } else {
                    // HYBRID or unrecognised — use router
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
                }
            }

            String context = buildCombinedContext(kbHits, githubHits);

            emit(emitter, "generate", "Generating answer",
                    Map.of("toolName", "llm_generate", "status", "running"));
            String sid = req.sessionId() == null || req.sessionId().isBlank() ? "anonymous" : req.sessionId();
            String finalAnswer = streamAnswer(req.safeMessage(), context, sid, emitter,
                    req.userEmail(), intent.toolHints());

            emitSourceCards(kbHits, emitter);
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

            emitSourceCards(kbHits, emitter);
            sse.sendAnswerFinal(emitter, finalAnswer);
            emit(emitter, "done", "Complete", Map.of("length", finalAnswer.length()));
            emitter.complete();
        } catch (Exception e) {
            handleError(emitter, e);
        }
    }



    // ── Source card emission ─────────────────────────────────────────────────

    /**
     * Enriches KB hits with source metadata and emits a {@code sources_found} SSE event.
     * Relevance is decided by vector-store similarity score inside
     * {@link SourceEnrichmentService#enrich(java.util.List, int)} — no linguistic heuristics.
     */
    private void emitSourceCards(List<Document> kbHits, SseEmitter emitter) {
        if (kbHits == null || kbHits.isEmpty()) return;
        try {
            List<SourceCardDto> cards = sourceEnrichment.enrich(kbHits, 3);
            if (cards.isEmpty()) {
                log.debug("Source cards filtered out: no chunks above similarity threshold");
                return;
            }
            List<Map<String, Object>> payload = cards.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.id());
                m.put("type", c.type());
                m.put("title", c.title());
                if (c.imageUrl() != null) m.put("imageUrl", c.imageUrl());
                m.put("url", c.url());
                if (c.publishedAt() != null) m.put("publishedAt", c.publishedAt());
                return m;
            }).toList();
            emit(emitter, "sources_found", "Related content found",
                    Map.of("sources", payload));
        } catch (Exception ex) {
            log.warn("Source card enrichment failed: {}", ex.getMessage());
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

    private String streamAnswer(String question, String context, String sessionId,
                                   SseEmitter emitter, String userEmail, List<String> toolHints) {
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
                .system(buildSystemPrompt(userEmail, toolHints))
                .user(userPrompt)
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, sessionId)
                        .param(ToolCallRecordingAdvisor.CTX_SESSION_ID, sessionId)
                        .param(ToolCallRecordingAdvisor.CTX_CHAT_MEMORY, chatMemory)
                        .param(ToolCallRecordingAdvisor.CTX_EMITTER, emitter)
                        .advisors(toolCallRecordingAdvisor,
                                  MessageChatMemoryAdvisor.builder(chatMemory).build()))
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
