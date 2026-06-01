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
 *   "mode": "FAST" | "DEEPTHINKING",
 *   "scopeMode": "OWNER_ONLY" | "GENERAL",
 *   "fileUrls": ["https://..."],  // optional
 *   "userEmail": "user@example.com"  // optional; set by ChatWidget when user is authenticated
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatRequest(
        String sessionId,
        String message,   // kept for backward-compat; prefer "question"
        String question,  // the field the ChatWidget actually sends
        String mode,      // FAST | DEEPTHINKING (default: FAST)
        String scopeMode,
        List<String> fileUrls,
        String userEmail  // Supabase-authenticated email; null/blank = anonymous
) {
    /** Returns the first non-blank of {@code question} / {@code message}. */
    public String safeMessage() {
        if (question != null && !question.isBlank()) return question.trim();
        return message == null ? "" : message.trim();
    }

    /** Normalises {@code userEmail}: trims whitespace and lower-cases; returns {@code ""} for null/blank. */
    public String safeUserEmail() {
        return (userEmail == null || userEmail.isBlank()) ? "" : userEmail.trim().toLowerCase();
    }

    /** Returns {@code true} only when the authenticated email matches the site owner. */
    public boolean isBlogOwner() {
        return "yuqi.guo17@gmail.com".equals(safeUserEmail());
    }

    public boolean isOwnerOnly() {
        return "OWNER_ONLY".equalsIgnoreCase(scopeMode);
    }

    public boolean isDeepThinking() { return "DEEPTHINKING".equalsIgnoreCase(mode); }
}
