package com.lawagent.api.report;

import com.lawagent.api.common.Result;
import com.lawagent.api.domain.AnalysisReport;
import com.lawagent.api.security.AuthUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @PostMapping("/{sessionId}")
  public Result<AnalysisReport> generate(@AuthenticationPrincipal AuthUser user, @PathVariable Long sessionId) {
    return Result.ok(reportService.generate(user.id(), sessionId));
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
    AnalysisReport report = reportService.ownedReport(user.id(), id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=legal-analysis-report.md")
        .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
        .body(report.getContentMd().getBytes(StandardCharsets.UTF_8));
  }
}
