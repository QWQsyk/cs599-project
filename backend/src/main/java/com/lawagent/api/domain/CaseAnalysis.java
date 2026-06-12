package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("case_analyses")
public class CaseAnalysis {
  private Long id;
  private Long sessionId;
  private String caseType;
  private String subType;
  private String claimGoalsJson;
  private Double completenessScore;
  private String conclusionLevel;
  private String factsJson;
  private String missingQuestionsJson;
  private String issuesJson;
  private String evidenceAssessmentsJson;
  private String evidenceJson;
  private String liabilityJson;
  private String actionPathJson;
  private String risksJson;
  private String citationsJson;
  private OffsetDateTime updatedAt;
}
