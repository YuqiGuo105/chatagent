package com.example.chatagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Request body sent by the Portfolio ChatWidget to
 * {@code POST /api/rag/answer/stream}.
 *
 * <p>The frontend sends {@code question} (not {@code message}); both field names
 * are accepted — {@code safeMessage()} returns whichever is present.</p>
 *
 * <pre>{@code
 * {
 *   "sessionId": "abc-123",
 *   "question": "What is the project about?",
 *   "mode": "FAST" | "DEEPTHINKING" | "WEB_GUIDE" | "ENHANCE",
 *   "scopeMode": "OWNER_ONLY" | "GENERAL",
 *   "fileUrls": ["https://..."]   // optional
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatRequest(
        String sessionId,
        String message,   // kept for backward-compat; prefer "question"
        String question,  // the field the ChatWidget actually sends
        String mode,      // FAST | DEEPTHINKING | WEB_GUIDE | ENHANCE (default: FAST)
        String scopeMode,
        List<String> fileUrls
) {
    /** Returns the first non-blank of {@code question} / {@code message}. */
    public String safeMessage() {
        if (question != null && !question.isBlank()) return question.trim();
        return message == null ? "" : message.trim();
    }

    public boolean isOwnerOnly() {
        return "OWNER_ONLY".equalsIgnoreCase(scopeMode);
    }

    public boolean isDeepThinking() { return "DEEPTHINKING".equalsIgnoreCase(mode); }
    public boolean isWebGuide()     { return "WEB_GUIDE".equalsIgnoreCase(mode); }
    public boolean isEnhance()      { return "ENHANCE".equalsIgnoreCase(mode); }
}
