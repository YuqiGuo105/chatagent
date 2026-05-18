package com.example.chatagent.common.exception;

/**
 * Custom exception for ChatAgent application
 */
public class ChatAgentException extends RuntimeException {

    private final String errorCode;

    public ChatAgentException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
    }

    public ChatAgentException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "INTERNAL_ERROR";
    }

    public ChatAgentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ChatAgentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
