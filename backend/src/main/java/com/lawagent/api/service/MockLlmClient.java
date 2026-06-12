package com.lawagent.api.service;

import com.lawagent.api.model.LlmModels.LlmRequest;
import com.lawagent.api.model.LlmModels.LlmResponse;
import org.springframework.stereotype.Component;

@Component
public class MockLlmClient implements LlmClient {
  @Override
  public LlmResponse complete(LlmRequest request) {
    return new LlmResponse("MVP mock response for " + request.promptCode(), "mock", 0, 0);
  }
}
