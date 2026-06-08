package com.example.writer.model;

public enum EventType {
    CONTENT_CREATED("content.created"),
    CONTENT_UPDATED("content.updated"),
    CONTENT_DELETED("content.deleted");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
