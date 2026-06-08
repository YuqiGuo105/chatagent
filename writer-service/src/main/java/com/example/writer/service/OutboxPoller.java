package com.example.writer.service;

import com.example.writer.model.OutboxEvent;
import com.example.writer.model.OutboxStatus;
import com.example.writer.outbox.ContentEventPublisher;
import com.example.writer.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final ContentEventPublisher publisher;

    @Scheduled(fixedDelayString = "${writer.outbox.poll-interval-ms:5000}")
    @Transactional
    public void poll() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.NEW);

        if (events.isEmpty()) return;

        log.debug("[OutboxPoller] Processing {} events", events.size());

        for (OutboxEvent event : events) {
            try {
                publisher.publish(event);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(OffsetDateTime.now());
            } catch (Exception e) {
                log.error("[OutboxPoller] Failed to publish event {}: {}", event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                if (event.getRetryCount() >= 5) {
                    event.setStatus(OutboxStatus.FAILED);
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
