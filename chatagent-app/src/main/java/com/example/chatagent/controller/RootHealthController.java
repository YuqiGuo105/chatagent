package com.example.chatagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight root-level health probe. The Portfolio ChatWidget probes
 * {@code GET <origin>/health} before opening an SSE stream.
 */
@RestController
public class RootHealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "chatagent");
        body.put("timestamp", System.currentTimeMillis());
        return body;
    }
}
