package com.lawagent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawagent.api.domain.ChatMessage;
import com.lawagent.api.domain.LegalSession;
import com.lawagent.api.mapper.ChatMessageMapper;
import com.lawagent.api.mapper.LegalSessionMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class LegalSessionService {
  private final LegalSessionMapper sessionMapper;
  private final ChatMessageMapper messageMapper;

  public LegalSessionService(LegalSessionMapper sessionMapper, ChatMessageMapper messageMapper) {
    this.sessionMapper = sessionMapper;
    this.messageMapper = messageMapper;
  }

  public record SessionDetail(LegalSession session, List<ChatMessage> messages) {
  }

  public LegalSession create(Long userId, String title) {
    LegalSession session = new LegalSession();
    session.setUserId(userId);
    session.setTitle(title == null || title.isBlank() ? "新的法律咨询" : title);
    session.setStatus("ACTIVE");
    session.setCreatedAt(OffsetDateTime.now());
    session.setUpdatedAt(OffsetDateTime.now());
    sessionMapper.insert(session);
    return session;
  }

  public List<LegalSession> list(Long userId) {
    return sessionMapper.selectList(new LambdaQueryWrapper<LegalSession>()
        .eq(LegalSession::getUserId, userId)
        .orderByDesc(LegalSession::getUpdatedAt));
  }

  public SessionDetail detail(Long userId, Long id) {
    LegalSession session = owned(userId, id);
    List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
        .eq(ChatMessage::getSessionId, id)
        .orderByAsc(ChatMessage::getCreatedAt));
    return new SessionDetail(session, messages);
  }

  public LegalSession rename(Long userId, Long id, String title) {
    LegalSession session = owned(userId, id);
    session.setTitle(title == null || title.isBlank() ? session.getTitle() : title);
    session.setUpdatedAt(OffsetDateTime.now());
    sessionMapper.updateById(session);
    return session;
  }

  public void delete(Long userId, Long id) {
    owned(userId, id);
    sessionMapper.deleteById(id);
  }

  public LegalSession owned(Long userId, Long id) {
    LegalSession session = sessionMapper.selectById(id);
    if (session == null || !session.getUserId().equals(userId)) {
      throw new IllegalArgumentException("会话不存在");
    }
    return session;
  }
}
