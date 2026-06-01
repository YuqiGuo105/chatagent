package com.example.chatagent.memory;

import com.example.chatagent.service.SseEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring AI advisor that intercepts tool calls and:
 * <ol>
 *   <li>Emits {@code tool_call_start} and {@code tool_call_result} SSE events so the
 *       frontend can render a visible tool-invocation timeline.</li>
 *   <li>Appends a concise tool-usage summary into {@link ChatMemory} so subsequent
 *       conversation turns know which tools were called.</li>
 * </ol>
 *
 * <p>Caller must pass {@link #CTX_EMITTER}, {@link #CTX_SESSION_ID}, and
 * {@link #CTX_CHAT_MEMORY} via {@code .param()} on the advisor spec.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallRecordingAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String CTX_EMITTER     = "ToolCallRecordingAdvisor.emitter";
    public static final String CTX_SESSION_ID  = "ToolCallRecordingAdvisor.sessionId";
    public static final String CTX_CHAT_MEMORY = "ToolCallRecordingAdvisor.chatMemory";

    private final SseEventWriter sse;

    @Override
    public String getName() { return "ToolCallRecordingAdvisor"; }

    @Override
    public int getOrder() { return 0; }

    // ── Synchronous path ─────────────────────────────────────────────────────

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        if (response != null) {
            recordFromResponse(response.chatResponse(), request.context(), start);
        }
        return response;
    }

    // ── Streaming path ────────────────────────────────────────────────────────

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        AtomicLong start = new AtomicLong(System.currentTimeMillis());
        return chain.nextStream(request)
                .doOnNext(r -> {
                    if (r != null) recordFromResponse(r.chatResponse(), request.context(), start.get());
                });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void recordFromResponse(ChatResponse response, Map<String, Object> ctx, long startMs) {
        if (response == null || response.getResult() == null) return;
        var output = response.getResult().getOutput();
        if (!(output instanceof AssistantMessage msg)) return;

        List<AssistantMessage.ToolCall> calls = msg.getToolCalls();
        if (calls == null || calls.isEmpty()) return;

        SseEmitter emitter = (SseEmitter)  ctx.get(CTX_EMITTER);
        String sessionId   = (String)      ctx.get(CTX_SESSION_ID);
        ChatMemory memory  = (ChatMemory)  ctx.get(CTX_CHAT_MEMORY);

        long latency = System.currentTimeMillis() - startMs;
        StringBuilder memEntry = new StringBuilder("[Tool calls in this turn]\n");

        for (AssistantMessage.ToolCall call : calls) {
            String toolName = call.name();
            String args     = call.arguments() == null ? "{}" : call.arguments();
            log.info("Tool invoked: {} args={}", toolName, args);

            if (emitter != null) {
                Map<String, Object> startPayload = new LinkedHashMap<>();
                startPayload.put("toolName", toolName);
                startPayload.put("args", truncate(args, 300));
                startPayload.put("callId", call.id());
                sse.sendStage(emitter, "tool_call_start", "Calling: " + toolName, startPayload);

                Map<String, Object> resultPayload = new LinkedHashMap<>();
                resultPayload.put("toolName", toolName);
                resultPayload.put("callId", call.id());
                resultPayload.put("latencyMs", latency);
                resultPayload.put("status", "invoked");
                sse.sendStage(emitter, "tool_call_result", "Done: " + toolName, resultPayload);
            }

            memEntry.append("- tool=").append(toolName)
                    .append(", args=").append(truncate(args, 200)).append("\n");
        }

        if (memory != null && sessionId != null && !sessionId.isBlank()) {
            try {
                memory.add(sessionId, List.of(new SystemMessage(memEntry.toString())));
            } catch (Exception e) {
                log.debug("ChatMemory tool-call write failed: {}", e.getMessage());
            }
        }
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) + "…" : s);
    }
}
