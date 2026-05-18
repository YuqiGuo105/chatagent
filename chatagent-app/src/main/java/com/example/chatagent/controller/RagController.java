package com.example.chatagent.controller;

import com.example.chatagent.dto.ChatRequest;
import com.example.chatagent.service.ChatService;
import com.example.chatagent.service.SseEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * RAG entry-points consumed by the Portfolio ChatWidget. The widget resolves
 * {@code <ASSIST_BASE>/answer/stream}; with {@code NEXT_PUBLIC_ASSIST_API=
 * https://.../api/rag}, requests land on {@code POST /api/rag/answer/stream}.
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final ChatService chatService;
    private final SseEventWriter sse;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "scope", "rag");
    }

    @PostMapping(value = "/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerStream(@RequestBody ChatRequest req) {
        log.info("/api/rag/answer/stream session={} scope={} msgLen={}",
                req.sessionId(), req.scopeMode(), req.safeMessage().length());
        SseEmitter emitter = sse.newEmitter();
        chatService.handle(req, emitter);
        return emitter;
    }
}
