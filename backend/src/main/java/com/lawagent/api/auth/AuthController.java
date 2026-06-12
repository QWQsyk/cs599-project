package com.lawagent.api.auth;

import com.lawagent.api.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public Result<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
    return Result.ok(authService.register(request));
  }

  @PostMapping("/login")
  public Result<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
    return Result.ok(authService.login(request));
  }
}
