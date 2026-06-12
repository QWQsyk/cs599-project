package com.lawagent.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "law-agent")
public record LawAgentProperties(
    String jwtSecret,
    String llmProvider,
    String llmApiKey,
    String llmBaseUrl,
    String llmModel,
    Integer llmTimeoutSeconds,
    String disclaimer
) {
}
