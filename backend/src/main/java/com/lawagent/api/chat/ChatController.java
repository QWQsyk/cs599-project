package com.lawagent.api.chat;

import com.lawagent.api.common.Result;
import com.lawagent.api.domain.ChatMessage;
import com.lawagent.api.security.AuthUser;
import com.lawagent.api.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @PostMapping("/messages")
  public Result<ChatMessage> send(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChatDtos.SendMessageRequest request) {
    return Result.ok(chatService.handleMessage(user.id(), request.sessionId(), request.content()).assistant());
  }

  @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@AuthenticationPrincipal AuthUser user, @PathVariable Long sessionId, @RequestParam String content) {
    return chatService.stream(user.id(), sessionId, content);
  }
}
