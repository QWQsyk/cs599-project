package com.lawagent.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawagent.api.service.ExternalLlmClient;
import com.lawagent.api.service.LlmClient;
import com.lawagent.api.service.MockLlmClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LawAgentProperties.class)
public class AppConfig {
  @Bean
  LlmClient llmClient(LawAgentProperties properties, ObjectMapper objectMapper) {
    String provider = properties.llmProvider() == null ? "mock" : properties.llmProvider();
    if ("mock".equalsIgnoreCase(provider) || "none".equalsIgnoreCase(provider)) {
      return new MockLlmClient();
    }
    return new ExternalLlmClient(
        provider,
        properties.llmApiKey(),
        properties.llmBaseUrl(),
        properties.llmModel(),
        properties.llmTimeoutSeconds() == null ? 45 : properties.llmTimeoutSeconds(),
        objectMapper
    );
  }
}
