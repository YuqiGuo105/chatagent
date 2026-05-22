package com.example.mcp.analytics.tool;

import com.example.mcp.analytics.service.VisitorAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VisitorAnalyticsTool {

    private final VisitorAnalyticsService service;

    @Tool(name = "analyze_visitor_logs",
          description = "Analyze Portfolio visitor logs and click events. " +
                        "analysis_type: overview | geographic | devices | traffic | clicks | events | geo_map. " +
                        "time_range: 1d | 7d | 30d | 90d | all.")
    public Map<String, Object> analyze(
            @ToolParam(description = "Analysis dimension: overview, geographic, devices, traffic, clicks, events, geo_map.")
            String analysisType,
            @ToolParam(description = "Time range bucket: 1d, 7d, 30d, 90d, all. Default 7d.",
                       required = false)
            String timeRange
    ) {
        String interval = service.resolveInterval(timeRange);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis_type", analysisType);
        result.put("time_range", timeRange == null ? "7d" : timeRange);

        Object data = switch (analysisType == null ? "overview" : analysisType.toLowerCase()) {
            case "overview"   -> service.overview(interval);
            case "geographic" -> service.geographic(interval);
            case "devices"    -> service.devices(interval);
            case "traffic"    -> service.traffic(interval);
            case "clicks"     -> service.clicks(interval);
            case "events"     -> service.events(interval);
            case "geo_map"    -> service.geoMap(interval);
            default -> Map.of("error", "Unknown analysis_type: " + analysisType +
                              ". Valid: overview, geographic, devices, traffic, clicks, events, geo_map");
        };
        result.put("data", data);
        return result;
    }
}
