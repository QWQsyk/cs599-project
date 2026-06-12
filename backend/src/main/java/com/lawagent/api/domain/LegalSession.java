package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("legal_sessions")
public class LegalSession {
  private Long id;
  private Long userId;
  private String title;
  private String caseType;
  private String status;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
