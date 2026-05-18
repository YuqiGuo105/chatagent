package com.example.chatagent.common.exception;

/**
 * Exception thrown when a resource is not found
 */
public class ResourceNotFoundException extends ChatAgentException {

    public ResourceNotFoundException(String resource, String identifier) {
        super("NOT_FOUND", String.format("%s not found: %s", resource, identifier));
    }

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
