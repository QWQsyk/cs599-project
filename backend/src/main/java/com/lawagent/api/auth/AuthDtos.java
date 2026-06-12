package com.lawagent.api.auth;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
  public record RegisterRequest(@NotBlank String username, @NotBlank String password, String displayName) {
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {
  }

  public record AuthResponse(String token, Long userId, String username, String roleCode) {
  }
}
