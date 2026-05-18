package com.example.chatagent.common.constant;

/**
 * Application-wide constants
 */
public class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    // Application info
    public static final String APP_NAME = "ChatAgent";
    public static final String APP_VERSION = "0.0.1-SNAPSHOT";

    // API endpoints
    public static final String API_V1_BASE = "/api/v1";
    public static final String API_CHAT = API_V1_BASE + "/chat";
    public static final String API_HEALTH = "/health";

    // Response messages
    public static final String SUCCESS = "Success";
    public static final String ERROR = "Error";

    // Default pagination
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // Cache configuration
    public static final String CACHE_CHAT = "chat";
    public static final int CACHE_TTL_MINUTES = 30;

}
