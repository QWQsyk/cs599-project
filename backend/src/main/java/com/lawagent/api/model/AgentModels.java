package com.lawagent.api.model;

import java.util.List;
import java.util.Map;

public class AgentModels {
  public record Citation(String type, String title, String sourceUrl) {
  }

  public record EvidenceGap(String fact, String priority, String suggestion) {
  }

  public record IssueAnalysis(
      String issue,
      List<String> requiredFacts,
      List<String> knownFacts,
      List<String> missingFacts,
      List<EvidenceGap> evidenceGaps,
      String evidenceStrength,
      String legalRule,
      String application,
      String conclusion
  ) {
  }

  public record EvidenceAssessment(String name, String purpose, String strength, boolean provided) {
  }

  public record AgentResult(
      String caseType,
      String subType,
      List<String> claimGoals,
      double completenessScore,
      String conclusionLevel,
      Map<String, Object> facts,
      List<String> missingQuestions,
      List<IssueAnalysis> issues,
      List<EvidenceAssessment> evidenceAssessments,
      List<String> evidenceList,
      List<String> liabilityAnalysis,
      List<String> actionPath,
      List<String> riskWarnings,
      List<Citation> citations,
      String userReply
  ) {
  }
}
