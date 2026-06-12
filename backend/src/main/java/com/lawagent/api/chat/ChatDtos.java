package com.lawagent.api.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChatDtos {
  public record CreateSessionRequest(String title) {
  }

  public record SendMessageRequest(@NotNull Long sessionId, @NotBlank String content) {
  }
}
