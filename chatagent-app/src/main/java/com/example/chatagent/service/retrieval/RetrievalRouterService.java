package com.example.chatagent.service.retrieval;

import com.example.chatagent.model.github.RetrievalRouteDecision;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lightweight keyword-based router that decides whether a user question should
 * be answered from the curated KB, from GitHub project content, GitHub code,
 * or a hybrid of both.
 *
 * <p>This is intentionally rule-based to stay deterministic + fast. Swap in an
 * LLM classifier later by re-implementing {@link #decideRoute(String)}.</p>
 */
@Service
public class RetrievalRouterService {

    private static final List<String> CODE_KEYWORDS = List.of(
            "code", "implement", "implementation", "function", "method", "class",
            "component", "service", "controller", "endpoint", "api", "snippet",
            "怎么实现", "代码", "文件", "组件", "如何", "实现", "源码"
    );

    private static final List<String> DOC_KEYWORDS = List.of(
            "readme", "doc", "docs", "documentation", "blog", "description",
            "module", "architecture",
            "文档", "博客", "描述", "模块", "架构", "说明"
    );

    private static final List<String> HYBRID_HINTS = List.of(
            "introduce", "explain", "overview", "tell me about",
            "介绍", "概述", "讲讲", "说说"
    );

    public RetrievalRouteDecision decideRoute(String question) {
        if (question == null || question.isBlank()) {
            return new RetrievalRouteDecision(RetrievalRouteDecision.ROUTE_KB,
                    "Empty question — default to KB", 0.5);
        }
        String q = question.toLowerCase();
        boolean hasCode = matches(q, CODE_KEYWORDS);
        boolean hasDoc = matches(q, DOC_KEYWORDS);
        boolean hasHybrid = matches(q, HYBRID_HINTS);

        if (hasCode && hasHybrid) {
            return new RetrievalRouteDecision(RetrievalRouteDecision.ROUTE_HYBRID,
                    "Mentions implementation alongside an overview-style cue", 0.75);
        }
        if (hasCode) {
            return new RetrievalRouteDecision(RetrievalRouteDecision.ROUTE_GITHUB_CODE,
                    "Question references implementation details", 0.8);
        }
        if (hasDoc) {
            return new RetrievalRouteDecision(RetrievalRouteDecision.ROUTE_GITHUB_CONTENT,
                    "Question references documentation / README content", 0.75);
        }
        return new RetrievalRouteDecision(RetrievalRouteDecision.ROUTE_KB,
                "Defaulting to the curated knowledge base", 0.6);
    }

    private boolean matches(String q, List<String> keywords) {
        for (String kw : keywords) {
            if (q.contains(kw)) return true;
        }
        return false;
    }
}
