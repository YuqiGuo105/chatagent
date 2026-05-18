package com.example.chatagent.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthControllerTests {

    @Test
    void testHealthControllerExists() {
        // Verify controller can be instantiated
        HealthController controller = new HealthController();
        assertTrue(controller != null);
    }
}
