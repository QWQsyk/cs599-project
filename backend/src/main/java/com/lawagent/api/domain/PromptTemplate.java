package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("prompt_templates")
public class PromptTemplate {
  private Long id;
  private String code;
  private String name;
  private String content;
  private Boolean enabled;
}
