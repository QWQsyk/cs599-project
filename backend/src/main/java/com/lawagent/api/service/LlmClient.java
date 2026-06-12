package com.lawagent.api.service;

import com.lawagent.api.model.LlmModels.LlmRequest;
import com.lawagent.api.model.LlmModels.LlmResponse;

public interface LlmClient {
  LlmResponse complete(LlmRequest request);
}
