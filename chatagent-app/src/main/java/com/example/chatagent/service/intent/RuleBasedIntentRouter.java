package com.example.chatagent.service.intent;

import com.example.chatagent.dto.ChatRequest;
import org.springframework.stereotype.Component;

/**
 * Zero-keyword intent router for the FAST pipeline.
 *
 * <p>Rather than hard-coding regex patterns per language, every request
 * runs RAG (KB_ONLY) so the LLM always has portfolio/biography context,
 * and the LLM autonomously chooses which registered tool to call
 * (the full set of tools is wired into {@code ChatClient.defaultTools(...)}
 * in {@code ChatService}).</p>
 *
 * <p>This trades a small, fixed retrieval latency for full multilingual
 * coverage and zero maintenance of keyword lists.</p>
 */
@Component
public class RuleBasedIntentRouter implements IntentRouter {

    @Override
    public IntentDecision decide(ChatRequest req) {
        return IntentDecision.rag("KB_ONLY",
                "Always retrieve KB context; LLM autonomously selects tools");
    }
}
