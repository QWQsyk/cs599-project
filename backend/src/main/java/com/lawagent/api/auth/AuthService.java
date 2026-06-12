package com.lawagent.api.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawagent.api.domain.User;
import com.lawagent.api.mapper.UserMapper;
import com.lawagent.api.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuthService {
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
    User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
    if (existing != null) {
      throw new IllegalArgumentException("用户名已存在");
    }
    User user = new User();
    user.setUsername(request.username());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setDisplayName(request.displayName() == null ? request.username() : request.displayName());
    user.setRoleCode("USER");
    user.setEnabled(true);
    user.setCreatedAt(OffsetDateTime.now());
    userMapper.insert(user);
    return toResponse(user);
  }

  public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
    User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
    if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new IllegalArgumentException("用户名或密码错误");
    }
    return toResponse(user);
  }

  private AuthDtos.AuthResponse toResponse(User user) {
    return new AuthDtos.AuthResponse(
        jwtService.issue(user.getId(), user.getUsername(), user.getRoleCode()),
        user.getId(),
        user.getUsername(),
        user.getRoleCode()
    );
  }
}
