package com.lawagent.api.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawagent.api.config.LawAgentProperties;
import com.lawagent.api.domain.AnalysisReport;
import com.lawagent.api.domain.CaseAnalysis;
import com.lawagent.api.domain.LegalSession;
import com.lawagent.api.mapper.AnalysisReportMapper;
import com.lawagent.api.mapper.CaseAnalysisMapper;
import com.lawagent.api.service.LegalSessionService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ReportService {
  private final LegalSessionService sessionService;
  private final CaseAnalysisMapper analysisMapper;
  private final AnalysisReportMapper reportMapper;
  private final LawAgentProperties properties;
  private final ObjectMapper objectMapper;

  public ReportService(LegalSessionService sessionService, CaseAnalysisMapper analysisMapper, AnalysisReportMapper reportMapper,
                       LawAgentProperties properties, ObjectMapper objectMapper) {
    this.sessionService = sessionService;
    this.analysisMapper = analysisMapper;
    this.reportMapper = reportMapper;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public AnalysisReport generate(Long userId, Long sessionId) {
    LegalSession session = sessionService.owned(userId, sessionId);
    CaseAnalysis analysis = analysisMapper.selectList(new LambdaQueryWrapper<CaseAnalysis>()
        .eq(CaseAnalysis::getSessionId, sessionId)
        .orderByDesc(CaseAnalysis::getUpdatedAt)
        .last("limit 1")).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("暂无案件分析结果"));

    AnalysisReport report = new AnalysisReport();
    report.setSessionId(sessionId);
    report.setUserId(userId);
    report.setTitle("法律责任初步分析报告 - " + session.getTitle());
    report.setContentMd("""
        # 法律责任初步分析报告

        %s

        ## 案件类型
        %s

        ## 案件子类型
        %s

        ## 诉求目标
        ```json
        %s
        ```

        ## 事实完整度
        %s%%

        ## 结论等级
        %s

        ## 事实摘要
        ```json
        %s
        ```

        ## 请求项成熟度
        ```json
        %s
        ```

        ## 缺失信息
        ```json
        %s
        ```

        ## 初步责任分析
        ```json
        %s
        ```

        ## 争点分析
        ```json
        %s
        ```

        ## 争点证据缺口
        以下内容来自争点分析中的 `evidenceGaps` 字段，按高/中优先级提示下一步补证方向。
        ```json
        %s
        ```

        ## 证据清单
        ```json
        %s
        ```

        ## 证据强度评估
        ```json
        %s
        ```

        ## 推荐维权路径
        ```json
        %s
        ```

        ## 风险提示
        ```json
        %s
        ```

        ## 引用来源
        ```json
        %s
        ```
        """.formatted(
        properties.disclaimer(),
        analysis.getCaseType(),
        analysis.getSubType(),
        analysis.getClaimGoalsJson(),
        analysis.getCompletenessScore() == null ? 0 : Math.round(analysis.getCompletenessScore() * 100),
        analysis.getConclusionLevel(),
        analysis.getFactsJson(),
        jsonField(analysis.getFactsJson(), "claimReadiness"),
        analysis.getMissingQuestionsJson(),
        analysis.getLiabilityJson(),
        analysis.getIssuesJson(),
        analysis.getIssuesJson(),
        analysis.getEvidenceJson(),
        analysis.getEvidenceAssessmentsJson(),
        analysis.getActionPathJson(),
        analysis.getRisksJson(),
        analysis.getCitationsJson()
    ));
    report.setCreatedAt(OffsetDateTime.now());
    reportMapper.insert(report);
    return report;
  }

  private String jsonField(String rawJson, String field) {
    try {
      JsonNode node = objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
      JsonNode value = node.get(field);
      return value == null ? "[]" : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (Exception ignored) {
      return "[]";
    }
  }

  public AnalysisReport ownedReport(Long userId, Long reportId) {
    AnalysisReport report = reportMapper.selectById(reportId);
    if (report == null || !report.getUserId().equals(userId)) {
      throw new IllegalArgumentException("报告不存在");
    }
    return report;
  }
}
