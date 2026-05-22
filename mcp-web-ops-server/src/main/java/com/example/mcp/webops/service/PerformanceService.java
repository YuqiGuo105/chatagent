package com.example.mcp.webops.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class PerformanceService {

    private final RestClient client = RestClient.create();

    public Map<String, Object> check(String url) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("url", url);
        long t0 = System.currentTimeMillis();
        try {
            ResponseEntity<String> resp = client.get().uri(url).retrieve().toEntity(String.class);
            long ms = System.currentTimeMillis() - t0;
            r.put("status_code", resp.getStatusCode().value());
            r.put("load_time_ms", ms);
            r.put("body_bytes", resp.getBody() == null ? 0 : resp.getBody().length());
            r.put("ok", resp.getStatusCode().is2xxSuccessful() && ms < 3_000);
        } catch (Exception e) {
            r.put("error", e.getMessage());
            r.put("load_time_ms", System.currentTimeMillis() - t0);
            r.put("ok", false);
        }
        return r;
    }
}
