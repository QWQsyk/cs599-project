package com.lawagent.api.model;

import java.util.Map;

public class LlmModels {
  public record LlmRequest(String promptCode, String systemPrompt, String userPrompt, Map<String, Object> variables) {
  }

  public record LlmResponse(String content, String provider, int inputTokens, int outputTokens) {
  }
}
