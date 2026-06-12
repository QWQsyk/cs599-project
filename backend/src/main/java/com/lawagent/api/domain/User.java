package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("users")
public class User {
  private Long id;
  private String username;
  private String passwordHash;
  private String displayName;
  private String roleCode;
  private Boolean enabled;
  private OffsetDateTime createdAt;
}
