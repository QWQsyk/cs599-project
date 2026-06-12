package com.lawagent.api.security;

import com.lawagent.api.config.LawAgentProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
  private final SecretKey key;

  public JwtService(LawAgentProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String issue(Long userId, String username, String roleCode) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(username)
        .claim("uid", userId)
        .claim("role", roleCode)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(7 * 24 * 3600)))
        .signWith(key)
        .compact();
  }

  public AuthUser parse(String token) {
    var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    return new AuthUser(
        ((Number) claims.get("uid")).longValue(),
        claims.getSubject(),
        String.valueOf(claims.get("role"))
    );
  }
}
