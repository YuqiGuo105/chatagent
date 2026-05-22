package com.example.chatagent.controller;

import com.example.chatagent.dto.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the blog management auth guard baked into ChatRequest.
 *
 * These tests verify that:
 *   1. Only the authorised owner email passes the isBlogOwner() check.
 *   2. safeUserEmail() normalises null/blank/whitespace/mixed-case emails.
 *   3. An anonymous (null) request is correctly identified as non-owner.
 */
class BlogAuthGuardTest {

    private static final String OWNER_EMAIL = "yuqi.guo17@gmail.com";

    private ChatRequest request(String email) {
        return new ChatRequest(null, null, "create a tech blog post", "FAST", "OWNER_ONLY", List.of(), email);
    }

    // ── isBlogOwner ──────────────────────────────────────────────────────────

    @Test
    void ownerEmail_returnsTrue() {
        assertTrue(request(OWNER_EMAIL).isBlogOwner(),
                "Exact owner email must be recognised");
    }

    @Test
    void ownerEmailUpperCase_returnsTrue() {
        assertTrue(request("YUQI.GUO17@GMAIL.COM").isBlogOwner(),
                "Case-insensitive comparison must work");
    }

    @Test
    void ownerEmailWithWhitespace_returnsTrue() {
        assertTrue(request("  yuqi.guo17@gmail.com  ").isBlogOwner(),
                "Leading/trailing whitespace must be trimmed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"other@gmail.com", "yuqi@example.com", "admin@example.com"})
    void nonOwnerEmail_returnsFalse(String email) {
        assertFalse(request(email).isBlogOwner(),
                "Non-owner emails must not be authorised");
    }

    @Test
    void nullEmail_returnsFalse() {
        assertFalse(request(null).isBlogOwner(),
                "null email must be treated as unauthenticated");
    }

    @Test
    void blankEmail_returnsFalse() {
        assertFalse(request("   ").isBlogOwner(),
                "Blank email must be treated as unauthenticated");
    }

    // ── safeUserEmail ────────────────────────────────────────────────────────

    @Test
    void safeUserEmail_null_returnsEmpty() {
        assertEquals("", request(null).safeUserEmail());
    }

    @Test
    void safeUserEmail_blank_returnsEmpty() {
        assertEquals("", request("  ").safeUserEmail());
    }

    @Test
    void safeUserEmail_normalises() {
        assertEquals("yuqi.guo17@gmail.com", request(" YUQI.GUO17@GMAIL.COM ").safeUserEmail());
    }

    // ── safeMessage ──────────────────────────────────────────────────────────

    @Test
    void safeMessage_prefersQuestion() {
        ChatRequest r = new ChatRequest(null, "old", "new", "FAST", null, List.of(), null);
        assertEquals("new", r.safeMessage());
    }

    @Test
    void safeMessage_fallsBackToMessage() {
        ChatRequest r = new ChatRequest(null, "fallback", null, "FAST", null, List.of(), null);
        assertEquals("fallback", r.safeMessage());
    }
}
