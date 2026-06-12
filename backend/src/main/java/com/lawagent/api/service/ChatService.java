package com.lawagent.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawagent.api.domain.CaseAnalysis;
import com.lawagent.api.domain.ChatMessage;
import com.lawagent.api.domain.LegalSession;
import com.lawagent.api.mapper.CaseAnalysisMapper;
import com.lawagent.api.mapper.ChatMessageMapper;
import com.lawagent.api.mapper.LegalSessionMapper;
import com.lawagent.api.model.AgentModels.AgentResult;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChatService {
  private final LegalSessionService sessionService;
  private final ChatMessageMapper messageMapper;
  private final LegalSessionMapper sessionMapper;
  private final CaseAnalysisMapper analysisMapper;
  private final LegalAgentService agentService;
  private final ObjectMapper objectMapper;

  public ChatService(LegalSessionService sessionService, ChatMessageMapper messageMapper, LegalSessionMapper sessionMapper,
                     CaseAnalysisMapper analysisMapper, LegalAgentService agentService, ObjectMapper objectMapper) {
    this.sessionService = sessionService;
    this.messageMapper = messageMapper;
    this.sessionMapper = sessionMapper;
    this.analysisMapper = analysisMapper;
    this.agentService = agentService;
    this.objectMapper = objectMapper;
  }

  public record ChatTurn(ChatMessage user, ChatMessage assistant, AgentResult analysis) {
  }

  public ChatTurn handleMessage(Long userId, Long sessionId, String content) {
    LegalSession session = sessionService.owned(userId, sessionId);
    ChatMessage userMessage = saveMessage(userId, sessionId, "USER", content, "{}");
    AgentResult result = agentService.analyze(mergedUserFacts(sessionId));
    ChatMessage assistant = saveMessage(userId, sessionId, "ASSISTANT", result.userReply(), toJson(result));
    upsertAnalysis(sessionId, result);
    session.setCaseType(result.caseType());
    session.setUpdatedAt(OffsetDateTime.now());
    sessionMapper.updateById(session);
    return new ChatTurn(userMessage, assistant, result);
  }

  private String mergedUserFacts(Long sessionId) {
    List<ChatMessage> userMessages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
        .eq(ChatMessage::getSessionId, sessionId)
        .eq(ChatMessage::getRole, "USER")
        .orderByAsc(ChatMessage::getCreatedAt));
    StringBuilder merged = new StringBuilder();
    for (int i = 0; i < userMessages.size(); i++) {
      merged.append("第").append(i + 1).append("次用户陈述：")
          .append(userMessages.get(i).getContent())
          .append("\n");
    }
    return merged.toString();
  }

  public SseEmitter stream(Long userId, Long sessionId, String content) {
    SseEmitter emitter = new SseEmitter(60_000L);
    new Thread(() -> {
      try {
        ChatTurn turn = handleMessage(userId, sessionId, content);
        for (String line : turn.assistant().getContent().split("\n")) {
          emitter.send(SseEmitter.event().name("message").data(line + "\n"));
          Thread.sleep(35);
        }
        emitter.send(SseEmitter.event().name("done").data(turn.assistant()));
        emitter.complete();
      } catch (Exception ex) {
        emitter.completeWithError(ex);
      }
    }).start();
    return emitter;
  }

  private ChatMessage saveMessage(Long userId, Long sessionId, String role, String content, String metadata) {
    ChatMessage message = new ChatMessage();
    message.setUserId(userId);
    message.setSessionId(sessionId);
    message.setRole(role);
    message.setContent(content);
    message.setMetadata(metadata);
    message.setCreatedAt(OffsetDateTime.now());
    messageMapper.insert(message);
    return message;
  }

  private void upsertAnalysis(Long sessionId, AgentResult result) {
    CaseAnalysis analysis = new CaseAnalysis();
    analysis.setSessionId(sessionId);
    analysis.setCaseType(result.caseType());
    analysis.setSubType(result.subType());
    analysis.setClaimGoalsJson(toJson(result.claimGoals()));
    analysis.setCompletenessScore(result.completenessScore());
    analysis.setConclusionLevel(result.conclusionLevel());
    analysis.setFactsJson(toJson(result.facts()));
    analysis.setMissingQuestionsJson(toJson(result.missingQuestions()));
    analysis.setIssuesJson(toJson(result.issues()));
    analysis.setEvidenceAssessmentsJson(toJson(result.evidenceAssessments()));
    analysis.setEvidenceJson(toJson(result.evidenceList()));
    analysis.setLiabilityJson(toJson(result.liabilityAnalysis()));
    analysis.setActionPathJson(toJson(result.actionPath()));
    analysis.setRisksJson(toJson(result.riskWarnings()));
    analysis.setCitationsJson(toJson(result.citations()));
    analysis.setUpdatedAt(OffsetDateTime.now());
    analysisMapper.insert(analysis);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{}";
    }
  }
}
