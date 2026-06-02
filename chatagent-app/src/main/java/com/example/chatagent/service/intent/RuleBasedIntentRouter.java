package com.example.chatagent.service.intent;

import com.example.chatagent.dto.ChatRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Zero-latency, rule-based intent router used for the FAST pipeline.
 *
 * <p>Patterns are checked in priority order; first match wins.
 * Unmatched requests default to {@link IntentDecision#free} — letting
 * the LLM decide freely with no extra hints.</p>
 */
@Component
public class RuleBasedIntentRouter implements IntentRouter {

    // ── Portfolio data patterns → query_portfolio_database ───────────────────
    private static final Pattern PORTFOLIO_DB = Pattern.compile(
            "(?i)\\b(projects?|blogs?|experiences?|skills?|work(s|ed|ing)?|jobs?|compan(y|ies)|roles?|技能|项目|博客|经历|作品|简历" +
            "|教育|公司|职位|published|tech.?stack|github\\.?url|demo|portfolio|life.?blog|life blog" +
            "|tech.?blog|what.*built|what.*made|when.*worked|where.*worked|latest.*project|recent.*project)\\b");

    // ── Site tour pattern → generate_site_tour ────────────────────────────────
    private static final Pattern SITE_TOUR = Pattern.compile(
            "(?i)\\b(tour|guide|walk.*through|导览|带我|带我看|带我了解|show.*around|介绍.*网站|site.*tour)\\b");

    // ── Web search patterns → web_search_with_highlight ──────────────────────
    private static final Pattern WEB_SEARCH = Pattern.compile(
            "(?i)\\b(search|搜索|搜一下|latest news|最新|news|current|trend|find.*article|look up)\\b");

    // ── Analytics patterns → analyze_visitor_logs ────────────────────────────
    private static final Pattern ANALYTICS = Pattern.compile(
            "(?i)\\b(visitor|traffic|analytics|访客|流量|统计|click|device|geographic|page.?view|pv|uv)\\b");

    // ── Source code / GitHub patterns → RAG (GITHUB_ONLY) ───────────────────
    private static final Pattern CODE = Pattern.compile(
            "(?i)\\b(code|implementation|source|class|method|function|import|dependency|代码|实现|源码|架构)\\b");

    // ── General knowledge base patterns → RAG (KB_ONLY) ─────────────────────
    private static final Pattern KB = Pattern.compile(
            "(?i)\\b(about|introduce|who|summary|biography|background|关于|介绍|简介|我是谁|你好|自我介绍)\\b");

    @Override
    public IntentDecision decide(ChatRequest req) {
        String q = req.safeMessage().toLowerCase();

        if (SITE_TOUR.matcher(q).find()) {
            return IntentDecision.tool("generate_site_tour",
                    "User wants a guided site tour");
        }
        if (ANALYTICS.matcher(q).find()) {
            return IntentDecision.tool("analyze_visitor_logs",
                    "User asking about visitor/analytics data");
        }
        if (PORTFOLIO_DB.matcher(q).find()) {
            return IntentDecision.ragPlusTool("KB_ONLY", "query_portfolio_database",
                    "User asking about portfolio projects/blogs/experience — RAG + SQL tool");
        }
        if (WEB_SEARCH.matcher(q).find()) {
            return IntentDecision.tool("web_search_with_highlight",
                    "User wants a web search");
        }
        if (CODE.matcher(q).find()) {
            return IntentDecision.rag("GITHUB_ONLY",
                    "User asking about code or implementation details");
        }
        if (KB.matcher(q).find()) {
            return IntentDecision.rag("KB_ONLY",
                    "User asking about background/biography content");
        }
        // Default: let the LLM decide
        return IntentDecision.free("No strong signal; LLM decides");
    }
}
