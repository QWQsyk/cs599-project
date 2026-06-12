package com.lawagent.api.service;

import com.lawagent.api.model.LlmModels.LlmRequest;
import com.lawagent.api.model.LlmModels.LlmResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ExternalLlmClient implements LlmClient {
  private final String provider;
  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public ExternalLlmClient(String provider, String apiKey, String baseUrl, String model, int timeoutSeconds, ObjectMapper objectMapper) {
    this.provider = provider;
    this.apiKey = apiKey;
    this.baseUrl = normalizeBaseUrl(provider, baseUrl);
    this.model = model == null || model.isBlank() ? defaultModel(provider) : model;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(timeoutSeconds <= 0 ? 45 : timeoutSeconds))
        .build();
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("未配置 LLM_API_KEY");
    }
    try {
      Map<String, Object> body = Map.of(
          "model", model,
          "temperature", 0.2,
          "response_format", Map.of("type", "json_object"),
          "messages", List.of(
              Map.of("role", "system", "content", request.systemPrompt()),
              Map.of("role", "user", "content", request.userPrompt())
          )
      );
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/chat/completions"))
          .timeout(Duration.ofSeconds(90))
          .header("content-type", "application/json")
          .header("authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
      });
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + response.body());
      }
      List<Map<String, Object>> choices = (List<Map<String, Object>>) payload.getOrDefault("choices", List.of());
      Map<String, Object> firstChoice = choices.isEmpty() ? Map.of() : choices.get(0);
      Map<String, Object> message = (Map<String, Object>) firstChoice.getOrDefault("message", Map.of());
      Map<String, Object> usage = (Map<String, Object>) payload.getOrDefault("usage", Map.of());
      return new LlmResponse(
          String.valueOf(message.getOrDefault("content", "")),
          provider,
          intValue(usage.get("prompt_tokens")),
          intValue(usage.get("completion_tokens"))
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("LLM 调用被中断", e);
    } catch (Exception e) {
      throw new IllegalStateException("LLM 调用失败: " + e.getMessage(), e);
    }
  }

  private static String normalizeBaseUrl(String provider, String configured) {
    String value = configured == null || configured.isBlank() ? defaultBaseUrl(provider) : configured;
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static String defaultBaseUrl(String provider) {
    String normalized = provider == null ? "" : provider.toLowerCase();
    if ("deepseek".equals(normalized)) return "https://api.deepseek.com";
    if ("qwen".equals(normalized) || "dashscope".equals(normalized) || "tongyi".equals(normalized)) {
      return "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }
    return "https://api.openai.com/v1";
  }

  private static String defaultModel(String provider) {
    String normalized = provider == null ? "" : provider.toLowerCase();
    if ("deepseek".equals(normalized)) return "deepseek-chat";
    if ("qwen".equals(normalized) || "dashscope".equals(normalized) || "tongyi".equals(normalized)) return "qwen-plus";
    return "gpt-4o-mini";
  }

  private static int intValue(Object value) {
    if (value instanceof Number number) return number.intValue();
    return 0;
  }
}
