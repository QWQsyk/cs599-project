package com.lawagent.api.caseanalysis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawagent.api.common.Result;
import com.lawagent.api.domain.CaseAnalysis;
import com.lawagent.api.mapper.CaseAnalysisMapper;
import com.lawagent.api.security.AuthUser;
import com.lawagent.api.service.LegalSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/case-analyses")
public class CaseAnalysisController {
  private final LegalSessionService sessionService;
  private final CaseAnalysisMapper analysisMapper;

  public CaseAnalysisController(LegalSessionService sessionService, CaseAnalysisMapper analysisMapper) {
    this.sessionService = sessionService;
    this.analysisMapper = analysisMapper;
  }

  @GetMapping("/{sessionId}")
  public Result<CaseAnalysis> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long sessionId) {
    sessionService.owned(user.id(), sessionId);
    CaseAnalysis analysis = analysisMapper.selectList(new LambdaQueryWrapper<CaseAnalysis>()
        .eq(CaseAnalysis::getSessionId, sessionId)
        .orderByDesc(CaseAnalysis::getUpdatedAt)
        .last("limit 1")).stream().findFirst().orElse(null);
    return Result.ok(analysis);
  }
}
