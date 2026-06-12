package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {
  private Long id;
  private Long sessionId;
  private Long userId;
  private String role;
  private String content;
  private String metadata;
  private OffsetDateTime createdAt;
}
