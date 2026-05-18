package com.example.chatagent.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTests {

    @Test
    void testSuccessResponse() {
        String testData = "test";
        ApiResponse<String> response = ApiResponse.success(testData);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertEquals("Success", response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error("TEST_ERROR", "Test error message");

        assertFalse(response.isSuccess());
        assertEquals("TEST_ERROR", response.getErrorCode());
        assertEquals("Test error message", response.getMessage());
        assertNotNull(response.getTimestamp());
    }
}
