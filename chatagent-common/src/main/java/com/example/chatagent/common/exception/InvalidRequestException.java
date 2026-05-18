package com.example.chatagent.common.exception;

/**
 * Exception thrown for invalid request parameters
 */
public class InvalidRequestException extends ChatAgentException {

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super("INVALID_REQUEST", message, cause);
    }
}
