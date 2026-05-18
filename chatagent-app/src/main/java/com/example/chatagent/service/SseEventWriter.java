package com.example.chatagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper that writes SSE events in the shape the Portfolio ChatWidget expects.
 *
 * <p>Each event payload is a JSON object like
 * {@code { "stage": "...", "message": "...", "payload": {...} }}.
 * The SSE {@code event:} name mirrors {@code stage} so the frontend can read
 * either {@code obj.stage} or {@code evt.event}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseEventWriter {

    private final ObjectMapper objectMapper;

    @Value("${app.sse.heartbeat-ms:15000}")
    private long heartbeatMs;

    /** Build an SseEmitter with a long timeout suitable for streaming LLM answers. */
    public SseEmitter newEmitter() {
        // 10 min; LLM streams should finish well before this
        SseEmitter emitter = new SseEmitter(600_000L);
        emitter.onCompletion(() -> log.debug("SSE emitter completed"));
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timed out");
            emitter.complete();
        });
        emitter.onError(err -> log.warn("SSE emitter error: {}", err.toString()));
        return emitter;
    }

    /** Send a generic stage event. */
    public void sendStage(SseEmitter emitter, String stage, String message, Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage", stage);
        if (message != null) body.put("message", message);
        body.put("payload", payload == null ? Map.of() : payload);
        send(emitter, stage, body);
    }

    /** Send a streaming token delta. */
    public void sendAnswerDelta(SseEmitter emitter, String delta) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delta", delta);
        sendStage(emitter, "answer_delta", null, payload);
    }

    /** Send the final assistant answer and complete the stream. */
    public void sendAnswerFinal(SseEmitter emitter, String fullText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", fullText);
        payload.put("content", fullText);
        sendStage(emitter, "answer_final", null, payload);
    }

    /** Send a structured error event then complete. */
    public void sendError(SseEmitter emitter, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        sendStage(emitter, "error", message, payload);
    }

    private void send(SseEmitter emitter, String eventName, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            emitter.send(SseEmitter.event()
                    .name(eventName == null ? "message" : eventName)
                    .data(json));
        } catch (JsonProcessingException jpe) {
            log.error("Failed to serialize SSE payload", jpe);
        } catch (IOException ioe) {
            // Client disconnected mid-stream; nothing to do but stop sending.
            log.debug("SSE write failed (client likely disconnected): {}", ioe.toString());
        } catch (IllegalStateException ise) {
            // Emitter already completed
            log.debug("SSE emitter already completed: {}", ise.toString());
        }
    }
}
