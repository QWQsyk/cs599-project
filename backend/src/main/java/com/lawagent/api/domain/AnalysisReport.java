package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("analysis_reports")
public class AnalysisReport {
  private Long id;
  private Long sessionId;
  private Long userId;
  private String title;
  private String contentMd;
  private OffsetDateTime createdAt;
}
