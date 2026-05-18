package com.example.chatagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * ChatAgent Application - Main entry point
 * AI-powered chat application with Spring Boot and PGVector
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.chatagent"})
public class ChatagentApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatagentApplication.class, args);
	}

}
