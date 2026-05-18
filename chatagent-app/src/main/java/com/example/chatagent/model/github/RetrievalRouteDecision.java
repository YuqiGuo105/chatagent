package com.example.chatagent.model.github;

/**
 * Result of {@code RetrievalRouterService.decideRoute()} — tells the chat
 * orchestrator which retrieval backend to consult.
 *
 * <p>Recognised routes: {@code kb_search}, {@code github_content_search},
 * {@code github_code_search}, {@code hybrid}.</p>
 */
public class RetrievalRouteDecision {
    public static final String ROUTE_KB = "kb_search";
    public static final String ROUTE_GITHUB_CONTENT = "github_content_search";
    public static final String ROUTE_GITHUB_CODE = "github_code_search";
    public static final String ROUTE_HYBRID = "hybrid";

    private final String route;
    private final String reason;
    private final double confidence;

    public RetrievalRouteDecision(String route, String reason, double confidence) {
        this.route = route;
        this.reason = reason;
        this.confidence = confidence;
    }

    public String getRoute() { return route; }
    public String getReason() { return reason; }
    public double getConfidence() { return confidence; }
}
