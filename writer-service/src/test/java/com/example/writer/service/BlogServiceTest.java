package com.example.writer.service;

import com.example.writer.dto.BlogRequest;
import com.example.writer.dto.BlogResponse;
import com.example.writer.exception.OptimisticLockConflictException;
import com.example.writer.model.OutboxEvent;
import com.example.writer.model.OutboxStatus;
import com.example.writer.repository.BlogRepository;
import com.example.writer.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BlogServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private BlogService blogService;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void create_persistsBlogAndOutboxEventInSameTransaction() {
        BlogRequest request = newRequest("hello-world");

        BlogResponse response = blogService.create(request, null);

        assertThat(response.getId()).isNotNull();
        assertThat(blogRepository.findById(response.getId())).isPresent();

        List<OutboxEvent> events = outboxEventRepository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.NEW);
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getEventType()).isEqualTo("content.created");
            assertThat(e.getAggregateId()).isEqualTo(response.getId().toString());
        });
    }

    @Test
    void update_incrementsVersionAndEmitsUpdatedEvent() {
        BlogResponse created = blogService.create(newRequest("update-test"), null);
        long initialVersion = created.getVersion();

        BlogRequest updated = newRequest("update-test");
        updated.setTitle("Updated Title");
        BlogResponse result = blogService.update(created.getId(), updated, initialVersion);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getVersion()).isGreaterThan(initialVersion);

        assertThat(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.NEW))
                .anySatisfy(e -> assertThat(e.getEventType()).isEqualTo("content.updated"));
    }

    @Test
    void delete_setsStatusDeletedAndEmitsDeletedEvent() {
        BlogResponse created = blogService.create(newRequest("delete-test"), null);

        blogService.delete(created.getId(), created.getVersion());

        assertThat(blogRepository.findById(created.getId()))
                .hasValueSatisfying(b -> assertThat(b.getStatus().name()).isEqualTo("DELETED"));

        assertThat(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.NEW))
                .anySatisfy(e -> assertThat(e.getEventType()).isEqualTo("content.deleted"));
    }

    @Test
    void update_withStaleVersion_throwsConflict() {
        BlogResponse created = blogService.create(newRequest("version-test"), null);
        BlogRequest update1 = newRequest("version-test");
        update1.setTitle("v1");
        blogService.update(created.getId(), update1, created.getVersion());

        // Now try to update with the old version
        BlogRequest update2 = newRequest("version-test");
        update2.setTitle("v2");
        assertThatThrownBy(() -> blogService.update(created.getId(), update2, created.getVersion()))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void create_isIdempotent_whenSameKeyProvided() {
        BlogRequest request = newRequest("idempotent-test");
        BlogResponse first = blogService.create(request, "key-123");
        BlogResponse second = blogService.create(request, "key-123");

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    private BlogRequest newRequest(String slug) {
        BlogRequest r = new BlogRequest();
        r.setTitle("Test Blog");
        r.setSlug(slug + "-" + UUID.randomUUID().toString().substring(0, 8));
        r.setDescription("desc");
        r.setContent("content");
        return r;
    }
}
