package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("legal_cases")
public class LegalCase {
  private Long id;
  private String title;
  private String caseType;
  private String summary;
  private String sourceUrl;
}
