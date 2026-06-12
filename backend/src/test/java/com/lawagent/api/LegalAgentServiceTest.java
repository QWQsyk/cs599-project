package com.lawagent.api;

import com.lawagent.api.config.LawAgentProperties;
import com.lawagent.api.rag.RagSearchService;
import com.lawagent.api.service.LegalAgentService;
import com.lawagent.api.service.PrivacyMaskService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalAgentServiceTest {
  @Test
  void classifiesLaborDisputeAndAsksMissingQuestions() {
    RagSearchService rag = mock(RagSearchService.class);
    when(rag.search("公司拖欠工资三个月且未签劳动合同", "劳动纠纷")).thenReturn(List.of());
    LegalAgentService service = new LegalAgentService(
        rag,
        new PrivacyMaskService(),
        new LawAgentProperties("change-me-change-me-change-me-change-me-32", "mock", "", "", "gpt-4o-mini", 45, "仅供初步参考")
    );

    var result = service.analyze("公司拖欠工资三个月且未签劳动合同");

    assertThat(result.caseType()).isEqualTo("劳动纠纷");
    assertThat(result.subType()).isIn("拖欠工资", "未签劳动合同");
    assertThat(result.issues()).isNotEmpty();
    assertThat(result.missingQuestions()).isNotEmpty();
    assertThat(result.userReply()).contains("仅供初步参考");
    assertThat(result.userReply()).contains("争点分析");
  }

  @Test
  void adaptsToLoanDisputeWithDifferentIssues() {
    RagSearchService rag = mock(RagSearchService.class);
    LegalAgentService service = new LegalAgentService(
        rag,
        new PrivacyMaskService(),
        new LawAgentProperties("change-me-change-me-change-me-change-me-32", "mock", "", "", "gpt-4o-mini", 45, "仅供初步参考")
    );

    var result = service.analyze("朋友借钱2万元不还，没有借条但有微信聊天和银行转账记录");

    assertThat(result.caseType()).isEqualTo("民间借贷纠纷");
    assertThat(result.subType()).isEqualTo("无借条但有转账");
    assertThat(result.issues()).extracting("issue").contains("借贷合意是否成立");
    assertThat(result.evidenceAssessments()).anyMatch(item -> item.name().contains("转账") && item.provided());
    assertThat(result.evidenceAssessments()).anyMatch(item -> item.name().contains("借条") && !item.provided());
    assertThat(result.userReply()).contains("借贷合意");
  }
}
