package com.example.chatagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ChatagentApplicationTests {

	@Test
	void contextLoads() {
		// Test that application context loads successfully
	}

}
