package com.example.chatagent.service.intent;

import com.example.chatagent.dto.ChatRequest;

import java.util.List;

/**
 * Decides whether RAG should be used for a given request, and provides
 * optional tool-hint suggestions to the system prompt.
 *
 * <p>This is a <em>hint</em> layer only — the LLM still sees all registered
 * tools and can call any of them regardless of the hints.</p>
 */
public interface IntentRouter {

    IntentDecision decide(ChatRequest req);

    record IntentDecision(
            boolean useRAG,
            String ragScope,          // KB_ONLY | GITHUB_ONLY | HYBRID | NONE
            List<String> toolHints,   // tool names to nudge towards (injected into system prompt)
            String reasoning          // logged for debugging
    ) {
        public static IntentDecision rag(String scope, String reason) {
            return new IntentDecision(true, scope, List.of(), reason);
        }
        public static IntentDecision tool(String toolName, String reason) {
            return new IntentDecision(false, "NONE", List.of(toolName), reason);
        }
        /** RAG retrieval runs AND the LLM is nudged to call the given tool with the retrieved context. */
        public static IntentDecision ragPlusTool(String scope, String toolName, String reason) {
            return new IntentDecision(true, scope, List.of(toolName), reason);
        }
        public static IntentDecision free(String reason) {
            return new IntentDecision(false, "NONE", List.of(), reason);
        }
    }
}
