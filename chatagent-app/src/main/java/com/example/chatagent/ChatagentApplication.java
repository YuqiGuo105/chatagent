package com.example.chatagent;

import com.example.chatagent.config.GitHubProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ChatAgent Application - Main entry point
 * AI-powered chat application with Spring Boot and PGVector
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(GitHubProperties.class)
@ComponentScan(basePackages = {"com.example.chatagent"})
public class ChatagentApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatagentApplication.class, args);
	}

}
