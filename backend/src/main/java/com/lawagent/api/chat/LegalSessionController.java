package com.lawagent.api.chat;

import com.lawagent.api.common.Result;
import com.lawagent.api.domain.LegalSession;
import com.lawagent.api.security.AuthUser;
import com.lawagent.api.service.LegalSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/legal-sessions")
public class LegalSessionController {
  private final LegalSessionService sessionService;

  public LegalSessionController(LegalSessionService sessionService) {
    this.sessionService = sessionService;
  }

  @PostMapping
  public Result<LegalSession> create(@AuthenticationPrincipal AuthUser user, @RequestBody ChatDtos.CreateSessionRequest request) {
    return Result.ok(sessionService.create(user.id(), request.title()));
  }

  @GetMapping
  public Result<List<LegalSession>> list(@AuthenticationPrincipal AuthUser user) {
    return Result.ok(sessionService.list(user.id()));
  }

  @GetMapping("/{id}")
  public Result<LegalSessionService.SessionDetail> detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
    return Result.ok(sessionService.detail(user.id(), id));
  }

  @PatchMapping("/{id}")
  public Result<LegalSession> rename(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestBody ChatDtos.CreateSessionRequest request) {
    return Result.ok(sessionService.rename(user.id(), id, request.title()));
  }

  @DeleteMapping("/{id}")
  public Result<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
    sessionService.delete(user.id(), id);
    return Result.ok(null);
  }
}
