package com.example.writer.controller;

import com.example.writer.config.AdminTokenFilter;
import com.example.writer.config.GlobalExceptionHandler;
import com.example.writer.dto.BlogRequest;
import com.example.writer.dto.BlogResponse;
import com.example.writer.exception.OptimisticLockConflictException;
import com.example.writer.model.ContentStatus;
import com.example.writer.model.ContentVisibility;
import com.example.writer.service.BlogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MockMvc test — bypasses Spring Security autoconfig and wires the
 * AdminTokenFilter manually so we can isolate filter + controller behaviour.
 */
class BlogControllerTest {

    private static final String VALID_TOKEN = "Bearer test-token";

    private MockMvc mockMvc;
    private BlogService blogService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        blogService = Mockito.mock(BlogService.class);
        BlogController controller = new BlogController(blogService);

        AdminTokenFilter filter = new AdminTokenFilter();
        ReflectionTestUtils.setField(filter, "adminToken", "test-token");

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filter)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void create_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/blogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withInvalidToken_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/blogs")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withValidTokenAndBody_returns201() throws Exception {
        Mockito.when(blogService.create(any(BlogRequest.class), nullable(String.class)))
                .thenReturn(mockResponse());

        mockMvc.perform(post("/api/admin/blogs")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    void create_withMissingTitle_returns400() throws Exception {
        BlogRequest req = validRequest();
        req.setTitle(""); // blank fails @NotBlank

        mockMvc.perform(post("/api/admin/blogs")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void update_withStaleVersion_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.when(blogService.update(eq(id), any(BlogRequest.class), eq(0L)))
                .thenThrow(new OptimisticLockConflictException("stale"));

        mockMvc.perform(put("/api/admin/blogs/" + id)
                        .header("Authorization", VALID_TOKEN)
                        .header("X-Expected-Version", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    private BlogRequest validRequest() {
        BlogRequest r = new BlogRequest();
        r.setTitle("Title");
        r.setSlug("valid-slug");
        return r;
    }

    private BlogResponse mockResponse() {
        return BlogResponse.builder()
                .id(UUID.randomUUID())
                .title("Title")
                .slug("valid-slug")
                .status(ContentStatus.DRAFT)
                .visibility(ContentVisibility.PUBLIC)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .version(0L)
                .build();
    }
}
