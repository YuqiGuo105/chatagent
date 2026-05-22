package com.example.mcp.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads from Portfolio's actual Supabase tables.
 *
 * visitor_logs columns:
 *   id, ip, local_time, event, ua, country, region, city,
 *   latitude, longitude, created_at
 *
 * visitor_clicks columns:
 *   id, click_event, target_url, local_time, ip, event, ua,
 *   country, region, city, latitude, longitude, created_at
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorAnalyticsService {

    private final JdbcTemplate jdbc;

    // -----------------------------------------------------------------
    // Time range helpers
    // -----------------------------------------------------------------

    public String resolveInterval(String range) {
        return switch (range == null ? "7d" : range.toLowerCase()) {
            case "1d"  -> "1 day";
            case "7d"  -> "7 days";
            case "30d" -> "30 days";
            case "90d" -> "90 days";
            case "all" -> null;
            default    -> "7 days";
        };
    }

    /** Returns a WHERE / AND fragment (without the keyword) for time filtering. */
    private String timeFilter(String interval) {
        return interval == null ? "" : "created_at >= NOW() - INTERVAL '" + interval + "'";
    }

    private String whereBlock(String interval) {
        String f = timeFilter(interval);
        return f.isEmpty() ? "" : " WHERE " + f;
    }

    private String andBlock(String interval) {
        String f = timeFilter(interval);
        return f.isEmpty() ? "" : " AND " + f;
    }

    // -----------------------------------------------------------------
    // Analysis methods
    // -----------------------------------------------------------------

    /** Basic counts: total visits, unique IPs, total clicks. */
    public Map<String, Object> overview(String interval) {
        String wb = whereBlock(interval);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total_visits",  jdbc.queryForObject(
                "SELECT COUNT(*) FROM visitor_logs" + wb, Long.class));
        out.put("unique_ips",    jdbc.queryForObject(
                "SELECT COUNT(DISTINCT ip) FROM visitor_logs" + wb, Long.class));
        out.put("total_clicks",  jdbc.queryForObject(
                "SELECT COUNT(*) FROM visitor_clicks" + wb, Long.class));
        out.put("recent_events", jdbc.queryForList(
                "SELECT ip, event, country, city, local_time " +
                "FROM visitor_logs" + wb + " " +
                "ORDER BY local_time DESC LIMIT 20"));
        return out;
    }

    /** Visits by country → region → city. */
    public List<Map<String, Object>> geographic(String interval) {
        return jdbc.queryForList(
                "SELECT country, region, city, COUNT(*) AS visits " +
                "FROM visitor_logs" + whereBlock(interval) + " " +
                "GROUP BY country, region, city ORDER BY visits DESC LIMIT 50");
    }

    /**
     * Device classification derived from the ua (user-agent) column.
     * Uses a SQL CASE expression so no extra library is needed.
     */
    public List<Map<String, Object>> devices(String interval) {
        return jdbc.queryForList(
                "SELECT " +
                "  CASE " +
                "    WHEN ua ILIKE '%mobile%' OR ua ILIKE '%android%' " +
                "      OR ua ILIKE '%iphone%' OR ua ILIKE '%windows phone%' THEN 'mobile' " +
                "    WHEN ua ILIKE '%tablet%' OR ua ILIKE '%ipad%' THEN 'tablet' " +
                "    WHEN ua IS NULL OR ua = '' THEN 'unknown' " +
                "    ELSE 'desktop' " +
                "  END AS device_type, " +
                "  COUNT(*) AS visits " +
                "FROM visitor_logs" + whereBlock(interval) + " " +
                "GROUP BY device_type ORDER BY visits DESC");
    }

    /** Daily visit counts (uses created_at for bucketing). */
    public List<Map<String, Object>> traffic(String interval) {
        return jdbc.queryForList(
                "SELECT DATE_TRUNC('day', created_at) AS day, COUNT(*) AS visits " +
                "FROM visitor_logs" + whereBlock(interval) + " " +
                "GROUP BY day ORDER BY day DESC LIMIT 90");
    }

    /** Top click events and target URLs from visitor_clicks. */
    public List<Map<String, Object>> clicks(String interval) {
        return jdbc.queryForList(
                "SELECT click_event, target_url, COUNT(*) AS clicks " +
                "FROM visitor_clicks" + whereBlock(interval) + " " +
                "GROUP BY click_event, target_url ORDER BY clicks DESC LIMIT 50");
    }

    /**
     * Custom event analysis — aggregates the `event` column from visitor_logs.
     * The event column records named actions (e.g. page views, button clicks).
     */
    public List<Map<String, Object>> events(String interval) {
        return jdbc.queryForList(
                "SELECT event, COUNT(*) AS occurrences " +
                "FROM visitor_logs " +
                "WHERE event IS NOT NULL" + andBlock(interval) + " " +
                "GROUP BY event ORDER BY occurrences DESC LIMIT 50");
    }

    /**
     * Returns lat/lng coordinates for mapping visitor origins.
     * Filters out null coordinates.
     */
    public List<Map<String, Object>> geoMap(String interval) {
        return jdbc.queryForList(
                "SELECT latitude, longitude, country, city, COUNT(*) AS visits " +
                "FROM visitor_logs " +
                "WHERE latitude IS NOT NULL AND longitude IS NOT NULL" + andBlock(interval) + " " +
                "GROUP BY latitude, longitude, country, city ORDER BY visits DESC LIMIT 200");
    }
}
