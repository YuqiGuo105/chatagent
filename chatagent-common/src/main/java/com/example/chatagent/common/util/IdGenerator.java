package com.example.chatagent.common.util;

import java.util.UUID;

/**
 * Utility class for ID generation
 */
public class IdGenerator {

    private IdGenerator() {
        // Prevent instantiation
    }

    /**
     * Generate a unique ID using UUID
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a short unique ID
     */
    public static String generateShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Generate a numeric ID from UUID
     */
    public static long generateNumericId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }
}
