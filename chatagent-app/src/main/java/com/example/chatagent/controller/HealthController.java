package com.example.chatagent.controller;

import com.example.chatagent.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check and info controller
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());
        data.put("application", "ChatAgent");
        
        return ResponseEntity.ok(ApiResponse.success("Health check passed", data));
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, String>>> info() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("name", "ChatAgent");
        data.put("version", "0.0.1-SNAPSHOT");
        data.put("description", "AI-powered chat application with Spring Boot and PGVector");
        
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
