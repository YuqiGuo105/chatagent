package com.example.chatagent.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdGeneratorTests {

    @Test
    void testGenerateId() {
        String id = IdGenerator.generateId();
        assertNotNull(id);
        assertFalse(id.isEmpty());
        assertEquals(36, id.length()); // UUID format: 8-4-4-4-12
    }

    @Test
    void testGenerateShortId() {
        String shortId = IdGenerator.generateShortId();
        assertNotNull(shortId);
        assertEquals(12, shortId.length());
    }

    @Test
    void testGenerateNumericId() {
        long numericId = IdGenerator.generateNumericId();
        assertTrue(numericId > 0);
    }

    @Test
    void testIdUniqueness() {
        String id1 = IdGenerator.generateId();
        String id2 = IdGenerator.generateId();
        assertNotEquals(id1, id2);
    }
}
