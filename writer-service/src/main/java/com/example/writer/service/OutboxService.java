package com.example.writer.service;

import com.example.writer.exception.ResourceNotFoundException;
import com.example.writer.model.*;
import com.example.writer.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Must be called inside an existing @Transactional boundary.
     * Inserts an outbox event row in the same transaction as the content write.
     */
    public void record(AggregateType aggregateType, String aggregateId,
                       EventType eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType.getValue())
                    .payload(json)
                    .status(OutboxStatus.NEW)
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
