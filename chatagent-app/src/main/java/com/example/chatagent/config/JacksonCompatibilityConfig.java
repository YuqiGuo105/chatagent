package com.example.chatagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a Jackson 2.x ObjectMapper bean so Spring AI 2.0.0-M6 (which depends on
 * com.fasterxml.jackson.databind.ObjectMapper) can start alongside Spring Boot 4.x
 * (which auto-configures tools.jackson.databind.ObjectMapper / Jackson 3.x).
 */
@Configuration
public class JacksonCompatibilityConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper jackson2ObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
