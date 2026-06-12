package com.lawagent.api.service;

import com.lawagent.api.config.LawAgentProperties;
import com.lawagent.api.model.AgentModels.AgentResult;
import com.lawagent.api.model.AgentModels.Citation;
import com.lawagent.api.model.AgentModels.EvidenceAssessment;
import com.lawagent.api.model.AgentModels.EvidenceGap;
import com.lawagent.api.model.AgentModels.IssueAnalysis;
import com.lawagent.api.rag.RagDtos;
import com.lawagent.api.rag.RagSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LegalAgentService {
  private static final Pattern AMOUNT_PATTERN = Pattern.compile("((?:人民币|¥)\\s*\\d+(?:\\.\\d+)?\\s*(?:元|万元|万|块)?|\\d+(?:\\.\\d+)?\\s*(?:万元|元|万|块))");
  private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}年\\d{1,2}月\\d{0,2}日?|\\d{1,2}月\\d{1,2}日|\\d+个?月|\\d+年)");
  private static final Pattern LOCATION_PATTERN = Pattern.compile("([\\p{IsHan}]{2,}(?:省|市|区|县|镇|街道|路|小区|公司|平台))");

  private final RagSearchService ragSearchService;
  private final PrivacyMaskService privacyMaskService;
  private final LawAgentProperties properties;

  public LegalAgentService(RagSearchService ragSearchService, PrivacyMaskService privacyMaskService, LawAgentProperties properties) {
    this.ragSearchService = ragSearchService;
    this.privacyMaskService = privacyMaskService;
    this.properties = properties;
  }

  public AgentResult analyze(String input) {
    String masked = privacyMaskService.mask(input == null ? "" : input.trim());
    CaseProfile profile = identifyCase(masked);
    Map<String, Object> facts = extractFacts(masked, profile);
    List<String> missingKeys = missingFactKeys(profile, facts);
    double completenessScore = completeness(profile, facts);
    List<String> missingQuestions = buildMissingQuestions(profile, missingKeys, facts);
    List<IssueAnalysis> issues = analyzeIssues(profile, facts);
    List<EvidenceAssessment> evidenceAssessments = assessEvidence(profile, facts);
    List<String> evidenceList = evidenceAssessments.stream()
        .map(item -> item.name() + "：" + item.purpose() + (item.provided() ? "（已出现线索）" : "（建议补充）"))
        .toList();
    List<String> liability = issues.stream().map(issue ->
        "【" + issue.issue() + "】" + issue.application() + " " + issue.conclusion()
    ).toList();
    List<RagDtos.RagHit> hits = completenessScore >= 0.45 ? ragSearchService.search(masked, profile.caseType()) : List.of();
    List<Citation> citations = new ArrayList<>(hits.stream()
        .map(hit -> new Citation(hit.type(), hit.title(), hit.sourceUrl()))
        .toList());
    citations.addAll(legalBasisCitations(profile, facts, issues));
    citations = uniqueCitations(citations);
    List<String> actionPath = buildActionPath(profile, completenessScore, facts);
    List<String> risks = buildRisks(profile, facts, completenessScore);
    String conclusionLevel = conclusionLevel(completenessScore, issues, facts);
    String reply = buildReply(profile, completenessScore, conclusionLevel, missingQuestions, issues, evidenceAssessments, actionPath, risks, citations);

    return new AgentResult(
        profile.caseType(),
        profile.subType(),
        profile.claimGoals(),
        completenessScore,
        conclusionLevel,
        facts,
        missingQuestions,
        issues,
        evidenceAssessments,
        evidenceList,
        liability,
        actionPath,
        risks,
        citations,
        reply
    );
  }

  private CaseProfile identifyCase(String text) {
    List<CaseProfile> candidates = List.of(
        laborProfile(text),
        leaseProfile(text),
        loanProfile(text),
        consumerProfile(text)
    );
    CaseProfile best = candidates.stream().max(Comparator.comparingInt(CaseProfile::score)).orElse(generalProfile(text));
    return best.score() == 0 ? generalProfile(text) : best;
  }

  private CaseProfile laborProfile(String text) {
    int score = score(text, "工资", "欠薪", "劳动合同", "辞退", "开除", "工伤", "加班", "社保", "仲裁", "公司", "入职");
    String subType = matchedSubTypes(text,
        List.of(
            rule("工伤赔偿", "工伤", "受伤", "职业病"),
            rule("违法解除", "辞退", "开除", "裁员", "解除"),
            rule("未签劳动合同", "未签", "没签", "没有签", "劳动合同"),
            rule("拖欠工资", "工资", "欠薪", "拖欠", "未发")
        ),
        "劳动关系综合争议"
    );
    return new CaseProfile("劳动纠纷", subType, score,
        goalsFor(subType, "确认劳动关系", "支付工资/赔偿", "准备劳动仲裁"),
        List.of("employmentStart", "employer", "salary", "unpaidPeriod", "laborEvidence"),
        List.of(
            evidence("劳动合同/录用通知", "证明劳动关系和岗位、薪资约定", "strong", "合同", "录用", "offer"),
            evidence("工资流水/转账记录", "证明工资标准和欠薪金额", "strong", "工资流水", "银行流水", "转账", "工资条"),
            evidence("考勤/排班/工作记录", "证明实际提供劳动", "medium", "考勤", "打卡", "排班", "工作群"),
            evidence("社保/个税记录", "辅助证明用工关系", "medium", "社保", "个税"),
            evidence("辞退通知/沟通记录", "证明解除事实和解除原因", "medium", "辞退", "开除", "解除通知", "聊天")
        )
    );
  }

  private CaseProfile leaseProfile(String text) {
    int score = score(text, "房东", "租房", "租赁", "押金", "租金", "退租", "维修", "中介", "房屋");
    String subType = matchedSubTypes(text,
        List.of(
            rule("押金返还", "押金", "不退押金", "扣押金"),
            rule("提前退租", "提前退租", "退租", "违约金"),
            rule("维修责任", "维修", "漏水", "损坏", "修理"),
            rule("租金违约", "租金", "欠租", "涨租")
        ),
        "房屋租赁综合争议"
    );
    return new CaseProfile("房屋租赁纠纷", subType, score,
        goalsFor(subType, "退还押金/租金", "确认违约责任", "解决维修或退租争议"),
        List.of("leaseContract", "depositAmount", "leaseTerm", "handover", "communication"),
        List.of(
            evidence("租赁合同", "证明租期、押金、违约条款", "strong", "合同", "租期"),
            evidence("押金/租金支付凭证", "证明已付款项和金额", "strong", "押金", "租金", "转账", "收据"),
            evidence("房屋交接清单/照片", "证明退租时房屋状态", "strong", "交接", "照片", "视频"),
            evidence("维修和退租沟通记录", "证明通知、协商和对方态度", "medium", "维修", "微信", "聊天", "通知")
        )
    );
  }

  private CaseProfile loanProfile(String text) {
    int score = score(text, "借钱", "借款", "欠钱", "还钱", "转账", "借条", "利息", "本金", "催款");
    List<String> loanSubTypes = new ArrayList<>();
    if (containsAny(text, "没有借条", "没借条", "无借条", "没有欠条")) loanSubTypes.add(containsAny(text, "转账", "流水") ? "无借条但有转账" : "仅有聊天记录");
    else if (containsAny(text, "借条", "欠条", "借据")) loanSubTypes.add("有借条借款");
    if (containsAny(text, "利息", "高利", "年利率")) loanSubTypes.add("利息争议");
    if (loanSubTypes.isEmpty() && containsAny(text, "聊天记录", "微信", "短信")) loanSubTypes.add("仅有聊天记录");
    String subType = loanSubTypes.isEmpty() ? "民间借贷综合争议" : String.join(" + ", loanSubTypes.stream().distinct().limit(3).toList());
    return new CaseProfile("民间借贷纠纷", subType, score,
        goalsFor(subType, "要求返还借款本金", "主张合法利息", "准备起诉/支付令"),
        List.of("loanAmount", "delivery", "repaymentDue", "loanAgreement", "borrowerIdentity"),
        List.of(
            evidence("借条/欠条/还款承诺", "证明借贷合意和还款义务", "strong", "借条", "欠条", "承诺"),
            evidence("转账/取现/收款记录", "证明款项交付", "strong", "转账", "流水", "收款"),
            evidence("聊天和催款记录", "证明借款用途、还款承诺、催告过程", "medium", "聊天", "微信", "催款"),
            evidence("对方身份信息", "用于确定被告和送达", "medium", "身份证", "手机号", "住址")
        )
    );
  }

  private CaseProfile consumerProfile(String text) {
    int score = score(text, "退款", "退货", "质量", "虚假宣传", "商家", "平台", "售后", "订单", "发票", "消费者");
    String subType = matchedSubTypes(text,
        List.of(
            rule("虚假宣传", "虚假宣传", "夸大", "承诺"),
            rule("商品质量问题", "质量", "坏了", "瑕疵", "假货"),
            rule("拒绝退款", "退款", "不退", "拒绝"),
            rule("平台售后纠纷", "平台", "售后", "客服")
        ),
        "消费维权综合争议"
    );
    return new CaseProfile("消费维权纠纷", subType, score,
        goalsFor(subType, "退款/退货/维修", "要求赔偿", "投诉平台或监管部门"),
        List.of("order", "payment", "defect", "merchantPromise", "afterSaleRecord"),
        List.of(
            evidence("订单和支付凭证", "证明交易关系和金额", "strong", "订单", "付款", "发票"),
            evidence("商品问题照片/检测记录", "证明质量或服务瑕疵", "strong", "照片", "检测", "质量", "坏"),
            evidence("宣传页面截图", "证明宣传内容和承诺", "medium", "宣传", "截图", "承诺"),
            evidence("售后沟通记录", "证明协商过程和商家态度", "medium", "售后", "客服", "聊天")
        )
    );
  }

  private CaseProfile generalProfile(String text) {
    return new CaseProfile("其他民事咨询", "待分流", 0, List.of("明确法律关系和维权目标"),
        List.of("parties", "timeline", "amount", "evidence"),
        List.of(
            evidence("合同或交易凭证", "证明基础法律关系", "strong", "合同", "协议", "订单"),
            evidence("付款记录", "证明财产往来", "strong", "转账", "付款", "流水"),
            evidence("沟通记录", "证明协商过程和对方承诺", "medium", "聊天", "微信", "短信")
        ));
  }

  private Map<String, Object> extractFacts(String text, CaseProfile profile) {
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("caseType", profile.caseType());
    facts.put("subType", profile.subType());
    facts.put("rawDescription", text);
    facts.put("caseTypeCandidates", caseTypeCandidates(text));
    facts.put("classificationAmbiguous", classificationAmbiguous(text));
    facts.put("amounts", findMatches(AMOUNT_PATTERN, text));
    facts.put("locations", findMatches(LOCATION_PATTERN, text));
    facts.put("jurisdictionKnown", hasJurisdictionInfo(text));
    facts.put("hasClaimAmount", hasAmount(text));
    facts.put("dates", findMatches(DATE_PATTERN, text));
    facts.put("contradictions", detectContradictions(text));
    facts.put("adverseFactors", detectAdverseFactors(profile, text));
    facts.put("urgentFactors", detectUrgentFactors(profile, text));
    facts.put("keyDateKnown", hasKeyDate(text));
    facts.put("partialRepayment", containsAny(text, "已还", "还了", "部分还款", "还过", "尚欠", "未还余额"));
    facts.put("interestAgreement", containsAny(text, "利息", "利率", "年利率", "月息", "逾期利息"));
    facts.put("withholdingReason", containsAny(text, "扣款", "扣押金", "损坏", "维修费", "清洁费", "违约金", "扣除"));
    facts.put("lossAmountKnown", hasAmount(text) || containsAny(text, "退款", "退货", "赔偿", "损失"));
    facts.put("hasWrittenContract", containsPositiveDocument(text));
    facts.put("hasChatRecord", containsAny(text, "聊天", "微信", "短信", "录音", "沟通"));
    facts.put("hasPaymentRecord", containsAny(text, "转账", "流水", "付款", "收据", "发票", "工资条"));
    facts.put("hasPhotoOrVideo", containsAny(text, "照片", "视频", "截图"));

    if ("劳动纠纷".equals(profile.caseType())) {
      facts.put("employmentStart", containsAny(text, "入职", "工作", "上班") || hasDate(text));
      facts.put("employer", containsAny(text, "公司", "单位", "老板", "用人单位"));
      facts.put("salary", containsAny(text, "工资", "薪资", "月薪") || hasAmount(text));
      facts.put("unpaidPeriod", containsAny(text, "拖欠", "未发", "欠薪") && hasDate(text));
      facts.put("laborEvidence", containsAny(text, "社保", "考勤", "打卡", "工资流水", "工作群", "工牌", "录用通知")
          || (containsAny(text, "劳动合同", "合同") && !containsAny(text, "未签", "没签", "没有签", "无合同")));
      facts.put("termination", containsAny(text, "辞退", "开除", "裁员", "解除"));
      facts.put("injury", containsAny(text, "工伤", "受伤", "事故"));
    } else if ("房屋租赁纠纷".equals(profile.caseType())) {
      facts.put("leaseContract", containsAny(text, "租赁合同", "合同", "协议"));
      facts.put("depositAmount", containsAny(text, "押金") || hasAmount(text));
      facts.put("leaseTerm", containsAny(text, "租期", "到期", "退租") || hasDate(text));
      facts.put("handover", containsAny(text, "交接", "钥匙", "验房", "照片"));
      facts.put("communication", containsAny(text, "微信", "聊天", "通知", "沟通"));
    } else if ("民间借贷纠纷".equals(profile.caseType())) {
      facts.put("loanAmount", hasAmount(text));
      facts.put("delivery", containsAny(text, "转账", "现金", "微信转", "支付宝", "银行卡"));
      facts.put("repaymentDue", containsAny(text, "还款", "到期", "约定", "期限") || hasDate(text));
      facts.put("loanAgreement", containsAny(text, "聊天记录", "承诺", "微信", "短信")
          || (containsAny(text, "借条", "欠条", "借据") && !containsAny(text, "没有借条", "没借条", "无借条", "没有欠条")));
      facts.put("borrowerIdentity", containsAny(text, "身份证", "手机号", "住址", "姓名"));
    } else if ("消费维权纠纷".equals(profile.caseType())) {
      facts.put("order", containsAny(text, "订单", "下单", "购买"));
      facts.put("payment", containsAny(text, "付款", "支付", "发票", "收据") || hasAmount(text));
      facts.put("defect", containsAny(text, "质量", "坏", "瑕疵", "假货", "不能用"));
      facts.put("merchantPromise", containsAny(text, "承诺", "宣传", "保证", "广告"));
      facts.put("afterSaleRecord", containsAny(text, "售后", "客服", "退款", "退货", "聊天"));
    }
    facts.put("timelineFocus", timelineFocus(profile, text, facts));
    facts.put("jurisdictionFocus", jurisdictionFocus(profile));
    facts.put("procedureFit", procedureFit(profile, facts));
    facts.put("claimReadiness", claimReadiness(profile, facts));
    return facts;
  }

  private List<IssueAnalysis> analyzeIssues(CaseProfile profile, Map<String, Object> facts) {
    return switch (profile.caseType()) {
      case "劳动纠纷" -> laborIssues(facts);
      case "房屋租赁纠纷" -> leaseIssues(facts);
      case "民间借贷纠纷" -> loanIssues(facts);
      case "消费维权纠纷" -> consumerIssues(facts);
      default -> List.of(genericIssue(facts));
    };
  }

  private List<IssueAnalysis> laborIssues(Map<String, Object> facts) {
    List<IssueAnalysis> issues = new ArrayList<>();
    issues.add(issue("是否能够证明劳动关系",
        List.of("用人单位主体", "入职/工作事实", "工资或管理关系", "劳动关系证据"),
        factKeys("employer", "employmentStart", "salary", "laborEvidence"), facts,
        "建立劳动关系通常需要结合用工管理、劳动报酬、工作内容等事实综合判断。",
        "若能提供工资流水、考勤、工作群、社保或录用材料，劳动关系证明力会明显增强。"));
    if (bool(facts, "salary")) {
      issues.add(issue("拖欠工资责任",
          List.of("工资标准", "拖欠期间", "支付记录", "劳动关系"),
          factKeys("salary", "unpaidPeriod", "hasPaymentRecord", "laborEvidence"), facts,
          "用人单位应按约定和法律规定及时足额支付劳动报酬。",
          "当前已有欠薪方向线索，关键在于固定工资标准、欠薪月份和实际支付记录。"));
      issues.add(issue("欠薪金额和请求范围",
          List.of("工资标准", "拖欠月份", "已发/未发记录", "请求金额"),
          factKeys("salary", "unpaidPeriod", "hasPaymentRecord", "hasClaimAmount"), facts,
          "工资请求需要把工资标准、拖欠期间、已支付金额和最终请求金额对应起来。",
          "如果只说拖欠工资但没有列清月份和金额，适合先核算明细，再决定投诉或仲裁请求。"));
    }
    if (!bool(facts, "hasWrittenContract")) {
      issues.add(issue("未签书面劳动合同责任",
          List.of("入职时间", "未签书面合同", "实际用工持续时间", "工资标准"),
          factKeys("employmentStart", "hasWrittenContract", "salary"), facts,
          "建立劳动关系后应及时订立书面劳动合同，逾期未签可能产生二倍工资差额等责任。",
          "用户称未签合同时，应优先补充入职时间、工作持续期间和工资标准，以便判断请求范围。"));
    }
    if (bool(facts, "termination")) {
      issues.add(issue("解除/辞退是否合法",
          List.of("解除通知", "解除理由", "工作年限", "工资标准", "规章制度依据"),
          factKeys("termination", "employmentStart", "salary"), facts,
          "解除劳动关系需有事实和制度依据，并遵守法定程序。",
          "仅知道被辞退还不足以判断违法解除，需要补充解除原因、通知方式和工作年限。"));
    }
    issues.add(issue("仲裁时效和程序前置风险",
        List.of("入职/离职或欠薪时间", "最近催告或争议发生时间"),
        factKeys("employmentStart", "keyDateKnown"), facts,
        "劳动争议通常需要先申请劳动仲裁，并结合争议发生、离职、催告等时间判断时效风险。",
        "时间线越明确，越能判断应先投诉、仲裁还是补强催告记录。"));
    appendAdverseIssue(issues, facts);
    return issues;
  }

  private List<IssueAnalysis> leaseIssues(Map<String, Object> facts) {
    List<IssueAnalysis> issues = new ArrayList<>(List.of(
        issue("押金/租金是否应返还",
            List.of("租赁合同", "押金金额", "退租时间", "房屋交接状态", "扣款依据"),
            factKeys("leaseContract", "depositAmount", "leaseTerm", "handover"), facts,
            "押金返还通常取决于合同约定、违约事实、房屋损耗和交接证据。",
            "如果没有房屋交接照片或扣款明细，押金争议的事实基础会偏弱。"),
        issue("押金扣款和返还金额口径",
            List.of("押金金额", "扣款理由", "扣款明细/维修凭证", "交接证据"),
            factKeys("depositAmount", "withholdingReason", "hasPaymentRecord", "handover"), facts,
            "押金争议不能只看是否退还，还要核对合同约定、扣款理由、实际损失和剩余应返金额。",
            "若房东没有提供扣款明细或损失凭证，返还请求方向会更清晰；若确有损坏，则需核算合理扣款。"),
        issue("维修责任或违约责任归属",
            List.of("损坏原因", "通知维修记录", "合同约定", "损失金额"),
            factKeys("leaseContract", "communication", "hasPhotoOrVideo"), facts,
            "维修责任需结合房屋自然损耗、承租人使用情况及合同约定判断。",
            "建议补齐照片、维修沟通和费用凭证后再判断责任比例。"),
        issue("租期、退租和起诉节点",
            List.of("租期/退租时间", "交接或扣款时间", "通知送达记录"),
            factKeys("leaseTerm", "keyDateKnown", "communication"), facts,
            "租赁争议的责任边界往往取决于合同期间、提前退租通知、交接和扣款发生时间。",
            "若能明确退租、交接和扣款日期，押金返还及违约责任判断会更稳定。")
    ));
    appendAdverseIssue(issues, facts);
    return issues;
  }

  private List<IssueAnalysis> loanIssues(Map<String, Object> facts) {
    List<IssueAnalysis> issues = new ArrayList<>(List.of(
        issue("借贷合意是否成立",
            List.of("借款意思表示", "借款金额", "交付凭证", "还款承诺"),
            factKeys("loanAgreement", "loanAmount", "delivery", "repaymentDue"), facts,
            "民间借贷通常需要证明双方存在借贷合意及款项已经交付。",
            "无借条并不必然不能主张，但需要用转账、聊天记录、催款记录形成证据链。"),
        issue("本金和利息能否支持",
            List.of("本金金额", "利息约定", "还款期限", "已还金额"),
            factKeys("loanAmount", "interestAgreement", "repaymentDue", "partialRepayment"), facts,
            "本金以实际交付为核心，利息需看约定及是否超过法定保护范围。",
            "当前应先明确本金、已还金额和利息约定，避免请求金额不清。"),
        issue("未还余额和请求金额口径",
            List.of("借款本金", "已还金额", "未还余额", "利息/逾期利息口径"),
            factKeys("loanAmount", "partialRepayment", "hasClaimAmount", "interestAgreement"), facts,
            "起诉或支付令请求应区分本金、已还金额、未还余额、利息起算点和计算标准。",
            "若只有借款总额但没有已还金额或利息约定，初步可判断借贷方向，但请求金额仍不稳定。"),
        issue("诉讼时效和催款节点",
            List.of("约定还款日", "最近催款/承认欠款时间", "对方身份送达信息"),
            factKeys("repaymentDue", "keyDateKnown", "borrowerIdentity"), facts,
            "民间借贷需要关注约定还款日、催收中断/重新确认以及诉讼送达条件。",
            "若还款日和最近催款时间不清，不能稳定判断是否存在时效抗辩风险。")
    ));
    appendAdverseIssue(issues, facts);
    return issues;
  }

  private List<IssueAnalysis> consumerIssues(Map<String, Object> facts) {
    List<IssueAnalysis> issues = new ArrayList<>(List.of(
        issue("商家是否构成违约或侵权",
            List.of("订单", "付款", "商品/服务问题", "宣传承诺", "售后记录"),
            factKeys("order", "payment", "defect", "merchantPromise", "afterSaleRecord"), facts,
            "消费维权通常围绕交易关系、商品或服务瑕疵、宣传承诺和损失结果展开。",
            "若能提供订单、问题照片、宣传截图和售后记录，退款或赔偿主张会更清晰。"),
        issue("平台或监管投诉路径",
            List.of("平台订单", "售后处理", "投诉记录", "损失金额"),
            factKeys("order", "afterSaleRecord", "payment"), facts,
            "平台售后和监管投诉重视交易凭证、问题证据和沟通记录。",
            "建议先通过平台规则和 12315 固定处理记录，再决定是否诉讼。"),
        issue("退款/赔偿金额和损失范围",
            List.of("支付金额", "退款或赔偿目标", "损失证明", "售后处理结果"),
            factKeys("payment", "lossAmountKnown", "defect", "afterSaleRecord"), facts,
            "消费维权需把支付金额、退款范围、质量问题或宣传差异造成的损失对应起来。",
            "若只说想维权但没有订单金额和损失范围，适合先明确退款、维修、更换或赔偿哪一项。"),
        issue("退换货、售后和投诉期限",
            List.of("购买/收货时间", "发现问题时间", "首次售后或投诉时间"),
            factKeys("order", "keyDateKnown", "afterSaleRecord"), facts,
            "消费维权处理效果通常受购买、收货、发现问题和首次售后时间影响。",
            "如果能明确时间节点，更容易选择平台售后、12315 投诉或诉讼路径。")
    ));
    appendAdverseIssue(issues, facts);
    return issues;
  }

  private void appendAdverseIssue(List<IssueAnalysis> issues, Map<String, Object> facts) {
    if (listFact(facts, "adverseFactors").isEmpty()) return;
    issues.add(issue("对方抗辩及反证压力",
        List.of("对方抗辩内容", "我方直接反证", "第三方或原始证据"),
        factKeys("adverseFactors", "hasChatRecord", "hasPaymentRecord"), facts,
        "责任判断不仅看己方陈述，也要评估对方可能提出的付款、赠与、质量、违约、损坏等抗辩。",
        "已出现对方抗辩线索时，应优先准备能直接回应该抗辩的原始证据，否则结论只能保持为初步可能。"));
  }

  private IssueAnalysis genericIssue(Map<String, Object> facts) {
    return issue("法律关系和请求基础是否明确",
        List.of("双方身份", "时间线", "金额/标的", "合同或沟通证据"),
        factKeys("hasWrittenContract", "hasPaymentRecord", "hasChatRecord"), facts,
        "民事责任判断需要先明确法律关系、请求权基础和证据链。",
        "当前信息不足以进行稳定责任判断，应先补充事实和证据。");
  }

  private IssueAnalysis issue(String title, List<String> requiredFacts, List<String> factKeys, Map<String, Object> facts,
                              String legalRule, String applicationBase) {
    List<String> known = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (int i = 0; i < factKeys.size(); i++) {
      String label = requiredFacts.get(Math.min(i, requiredFacts.size() - 1));
      if (bool(facts, factKeys.get(i))) known.add(label);
      else missing.add(label);
    }
    String strength = strength(known.size(), factKeys.size());
    List<EvidenceGap> evidenceGaps = evidenceGaps(title, missing, strength);
    String conclusion = switch (strength) {
      case "strong" -> "初步看该争点具备较好的事实基础，但仍需以原始证据和正式审查为准。";
      case "medium" -> "该争点存在可主张方向，但证据链仍需补强。";
      default -> "该争点目前事实不足，建议先补充关键材料后再判断。";
    };
    return new IssueAnalysis(title, requiredFacts, known, missing, evidenceGaps, strength, legalRule, applicationBase, conclusion);
  }

  private List<EvidenceGap> evidenceGaps(String issueTitle, List<String> missingFacts, String strength) {
    List<EvidenceGap> gaps = new ArrayList<>();
    for (int i = 0; i < missingFacts.size(); i++) {
      String fact = missingFacts.get(i);
      String priority = ("weak".equals(strength) || i == 0) ? "high" : "medium";
      gaps.add(new EvidenceGap(fact, priority, evidenceSuggestion(issueTitle, fact)));
    }
    return gaps;
  }

  private String evidenceSuggestion(String issueTitle, String fact) {
    String joined = issueTitle + " " + fact;
    if (containsAny(joined, "劳动关系", "用人单位", "入职", "工作事实")) return "优先补劳动合同、录用通知、考勤、工作群、工牌、社保或工资流水。";
    if (containsAny(joined, "工资", "欠薪", "已发", "未发")) return "补工资标准约定、工资流水、工资条、欠薪月份表和催要记录。";
    if (containsAny(joined, "解除", "辞退")) return "补解除通知、聊天记录、录音、离职交接材料和公司说明。";
    if (containsAny(joined, "押金", "租赁", "租期", "交接")) return "补租赁合同、押金转账、退租交接照片/视频、钥匙交接和扣款明细。";
    if (containsAny(joined, "维修", "损坏", "扣款")) return "补损坏前后照片、维修报价/发票、房东扣款说明和沟通记录。";
    if (containsAny(joined, "借款", "本金", "交付", "还款", "借贷")) return "补转账流水、聊天承诺、借条/欠条、催款记录、已还款明细和对方身份信息。";
    if (containsAny(joined, "利息", "未还余额")) return "补利息约定、还款明细、未还余额计算表和催款确认记录。";
    if (containsAny(joined, "订单", "商品", "服务", "宣传", "售后", "退款")) return "补订单、支付凭证、宣传截图、问题照片/视频、检测记录和售后沟通。";
    if (containsAny(joined, "抗辩", "反证")) return "围绕对方说法补直接反证，例如原始聊天、付款备注、第三方记录或现场照片。";
    if (containsAny(joined, "时间", "期限", "时效")) return "补关键日期的原始凭证，例如合同、转账时间、催告记录、平台工单或聊天截图。";
    return "补能直接证明该事实的原始材料，优先使用带时间、主体和金额信息的证据。";
  }

  private List<EvidenceAssessment> assessEvidence(CaseProfile profile, Map<String, Object> facts) {
    String raw = String.valueOf(facts.get("rawDescription"));
    return profile.evidenceRules().stream()
        .map(rule -> new EvidenceAssessment(rule.name(), rule.purpose(), rule.strength(), evidenceProvided(rule, raw)))
        .toList();
  }

  private List<Citation> legalBasisCitations(CaseProfile profile, Map<String, Object> facts, List<IssueAnalysis> issues) {
    List<Citation> citations = new ArrayList<>();
    String flk = "https://flk.npc.gov.cn/";
    switch (profile.caseType()) {
      case "劳动纠纷" -> {
        citations.add(new Citation("article", "《中华人民共和国劳动合同法》：劳动合同订立、工资支付和解除相关规则", flk));
        citations.add(new Citation("article", "《中华人民共和国劳动争议调解仲裁法》：劳动仲裁程序和时效相关规则", flk));
      }
      case "房屋租赁纠纷" -> citations.add(new Citation("article", "《中华人民共和国民法典》：租赁合同、违约责任和押金返还相关规则", flk));
      case "民间借贷纠纷" -> {
        citations.add(new Citation("article", "《中华人民共和国民法典》：借款合同、合同履行和违约责任相关规则", flk));
        citations.add(new Citation("article", "最高人民法院关于审理民间借贷案件适用法律若干问题的规定", "https://www.court.gov.cn/"));
      }
      case "消费维权纠纷" -> {
        citations.add(new Citation("article", "《中华人民共和国消费者权益保护法》：消费者退款、赔偿和经营者责任相关规则", flk));
        citations.add(new Citation("article", "《中华人民共和国产品质量法》：产品质量责任相关规则", flk));
      }
      default -> citations.add(new Citation("article", "《中华人民共和国民法典》：民事主体、合同和侵权责任的一般规则", flk));
    }
    if (bool(facts, "classificationAmbiguous")) {
      citations.add(new Citation("guidance", "案由交叉时需先确认基础法律关系，再分别检索对应规则", flk));
    }
    if (!listFact(facts, "adverseFactors").isEmpty()) {
      citations.add(new Citation("guidance", "存在对方抗辩时，应围绕争议事实补充反证后再作结论", flk));
    }
    return citations;
  }

  private List<Citation> uniqueCitations(List<Citation> citations) {
    Map<String, Citation> unique = new LinkedHashMap<>();
    for (Citation citation : citations) {
      unique.putIfAbsent(citation.type() + "|" + citation.title() + "|" + citation.sourceUrl(), citation);
    }
    return new ArrayList<>(unique.values());
  }

  private boolean evidenceProvided(EvidenceRule rule, String raw) {
    if (rule.name().contains("劳动合同") && containsAny(raw, "未签", "没签", "没有签", "无合同", "没有合同")) {
      return false;
    }
    if ((rule.name().contains("借条") || rule.name().contains("欠条")) && containsAny(raw, "没有借条", "没借条", "无借条", "没有欠条")) {
      return false;
    }
    return containsAny(raw, rule.keywords().toArray(String[]::new));
  }

  private List<String> missingFactKeys(CaseProfile profile, Map<String, Object> facts) {
    return profile.requiredFactKeys().stream().filter(key -> !bool(facts, key)).toList();
  }

  private double completeness(CaseProfile profile, Map<String, Object> facts) {
    if (profile.requiredFactKeys().isEmpty()) return 0;
    long present = profile.requiredFactKeys().stream().filter(key -> bool(facts, key)).count();
    return Math.round((present * 100.0 / profile.requiredFactKeys().size())) / 100.0;
  }

  private List<String> buildMissingQuestions(CaseProfile profile, List<String> missingKeys, Map<String, Object> facts) {
    List<String> questions = new ArrayList<>();
    for (String contradiction : listFact(facts, "contradictions")) {
      questions.add("请先澄清：" + contradiction);
    }
    if (bool(facts, "classificationAmbiguous")) {
      questions.add("当前描述可能同时涉及多个法律关系，请先确认主诉求和对方身份：是劳动用工、消费退款、借贷返还、租赁押金，还是多个请求需要分别处理？");
    }
    for (String adverse : listFact(facts, "adverseFactors")) {
      questions.add("请补充能回应对方抗辩的证据：" + adverse);
    }
    for (String urgent : listFact(facts, "urgentFactors")) {
      questions.add("请优先确认紧急情况：" + urgent);
    }
    if (!bool(facts, "keyDateKnown")) {
      questions.add(String.valueOf(facts.get("timelineFocus")));
    }
    if (!bool(facts, "jurisdictionKnown")) {
      questions.add(String.valueOf(facts.get("jurisdictionFocus")));
    }
    questions.addAll(amountQuestions(profile, facts));
    questions.addAll(missingKeys.stream()
        .map(key -> questionFor(profile.caseType(), key))
        .distinct()
        .limit(6)
        .toList());
    return questions.stream().distinct().limit(8).toList();
  }

  private String questionFor(String caseType, String key) {
    Map<String, String> questions = new LinkedHashMap<>();
    questions.put("employmentStart", "请补充入职时间、工作岗位、工作地点，以及是否仍在职。");
    questions.put("employer", "请补充用人单位名称、与你对接的负责人，以及工资由谁支付。");
    questions.put("salary", "请补充工资标准、发薪周期、拖欠金额和拖欠月份。");
    questions.put("unpaidPeriod", "请补充从哪几个月开始拖欠工资，是否有工资条或银行流水。");
    questions.put("laborEvidence", "请说明是否有劳动合同、考勤、社保、工作群、工牌或工资流水。");
    questions.put("leaseContract", "请补充租赁合同中关于押金、租期、违约和维修责任的约定。");
    questions.put("depositAmount", "请补充押金/租金金额、支付方式和付款凭证。");
    questions.put("leaseTerm", "请补充租期、退租时间、是否提前退租以及提前退租原因。");
    questions.put("handover", "请补充退租交接记录、房屋照片/视频和房东扣款明细。");
    questions.put("communication", "请补充你与房东/中介关于退租、维修或扣款的聊天记录。");
    questions.put("loanAmount", "请补充借款金额、币种、实际交付时间和已还金额。");
    questions.put("delivery", "请补充款项是现金还是转账交付，是否有银行/微信/支付宝流水。");
    questions.put("repaymentDue", "请补充约定还款时间、催款时间以及对方是否承认欠款。");
    questions.put("loanAgreement", "请说明是否有借条、欠条、聊天记录、语音或还款承诺。");
    questions.put("borrowerIdentity", "请补充对方姓名、手机号、住址或身份证等可用于起诉的信息。");
    questions.put("order", "请补充订单号、购买时间、平台或商家名称。");
    questions.put("payment", "请补充支付金额、支付凭证、发票或收据。");
    questions.put("defect", "请描述商品/服务具体问题，并说明是否有照片、视频或检测记录。");
    questions.put("merchantPromise", "请补充商家宣传页面、承诺内容或销售话术截图。");
    questions.put("afterSaleRecord", "请补充售后沟通、退款申请、平台处理记录。");
    return questions.getOrDefault(key, "请补充" + caseType + "中与“" + key + "”相关的事实和证据。");
  }

  private List<String> amountQuestions(CaseProfile profile, Map<String, Object> facts) {
    List<String> questions = new ArrayList<>();
    switch (profile.caseType()) {
      case "劳动纠纷" -> {
        if (bool(facts, "salary") && !bool(facts, "hasClaimAmount")) {
          questions.add("请列明每月工资标准、拖欠月份、已发金额和你准备主张的欠薪总额。");
        }
      }
      case "房屋租赁纠纷" -> {
        if (bool(facts, "depositAmount") && !bool(facts, "withholdingReason")) {
          questions.add("请补充房东拒退或扣押金的具体理由、扣款明细，以及是否有维修/清洁/损坏凭证。");
        }
      }
      case "民间借贷纠纷" -> {
        if (bool(facts, "loanAmount") && !bool(facts, "partialRepayment")) {
          questions.add("请补充对方已还金额、未还余额、是否约定利息，以及你准备主张的本金和利息计算方式。");
        }
      }
      case "消费维权纠纷" -> {
        if (bool(facts, "payment") && !bool(facts, "lossAmountKnown")) {
          questions.add("请补充订单支付金额、希望退款/维修/更换/赔偿的具体请求，以及损失如何计算。");
        }
      }
      default -> {
        if (!bool(facts, "hasClaimAmount")) questions.add("请补充你准备主张的金额、计算依据和已付款/已还款情况。");
      }
    }
    return questions;
  }

  private List<String> buildActionPath(CaseProfile profile, double completenessScore, Map<String, Object> facts) {
    List<String> steps = new ArrayList<>();
    if (completenessScore < 0.6) {
      steps.add("先按追问清单补充事实，避免在关键信息不足时直接判断责任。");
      steps.add("立即保存原始证据，不要只保存转述内容。");
    }
    if (!bool(facts, "keyDateKnown")) {
      steps.add("先补一张时间线：发生时间、通知/催告时间、对方回应时间和目前状态。");
    }
    if (!bool(facts, "jurisdictionKnown")) {
      steps.add("补充管辖地点信息，确认应向哪个劳动仲裁委、法院、平台或监管部门提交材料。");
    }
    for (String urgent : listFact(facts, "urgentFactors")) {
      steps.add("紧急处理：" + urgent);
    }
    switch (profile.caseType()) {
      case "劳动纠纷" -> {
        steps.add("整理劳动关系证据、工资标准和欠薪明细。");
        steps.add(String.valueOf(facts.get("procedureFit")));
        steps.add("注意劳动仲裁时效，尽量保留书面催告和沟通记录。");
      }
      case "房屋租赁纠纷" -> {
        steps.add("整理合同、付款凭证、交接照片和扣款明细。");
        steps.add("先书面通知房东/中介说明请求，保留送达记录。");
        steps.add(String.valueOf(facts.get("procedureFit")));
      }
      case "民间借贷纠纷" -> {
        steps.add("整理借贷合意、款项交付、催款和对方身份信息。");
        steps.add("发送书面催款通知，固定对方是否承认欠款。");
        steps.add(String.valueOf(facts.get("procedureFit")));
      }
      case "消费维权纠纷" -> {
        steps.add("整理订单、支付、问题证据、宣传页面和售后记录。");
        steps.add(String.valueOf(facts.get("procedureFit")));
      }
      default -> steps.add("先明确双方身份、法律关系、请求目标和证据基础。");
    }
    return steps;
  }

  private List<String> buildRisks(CaseProfile profile, Map<String, Object> facts, double completenessScore) {
    List<String> risks = new ArrayList<>();
    risks.add("仅供初步参考，不构成正式法律意见；不要把本分析直接等同于法院或仲裁结果。");
    risks.add("不要伪造、篡改、隐瞒证据，也不要诱导对方作虚假陈述。");
    for (String contradiction : listFact(facts, "contradictions")) {
      risks.add("存在事实冲突：" + contradiction + " 在澄清前不宜据此作强结论。");
    }
    if (bool(facts, "classificationAmbiguous")) {
      risks.add("案由识别存在交叉：" + String.join("、", listFact(facts, "caseTypeCandidates")) + "。若法律关系分流错误，后续责任和路径判断会偏差。");
    }
    for (String adverse : listFact(facts, "adverseFactors")) {
      risks.add("对方可能抗辩：" + adverse + " 若缺少直接反证，分析结论应下调为审慎判断。");
    }
    for (String urgent : listFact(facts, "urgentFactors")) {
      risks.add("紧急风险：" + urgent + " 建议优先固定证据并尽快选择正式程序。");
    }
    if (completenessScore < 0.6) risks.add("当前事实完整度偏低，直接判断责任可能误导，应优先补充关键事实。");
    if (!bool(facts, "keyDateKnown")) risks.add(String.valueOf(facts.get("timelineFocus")));
    if (!bool(facts, "jurisdictionKnown")) risks.add(String.valueOf(facts.get("jurisdictionFocus")));
    risks.addAll(amountRisks(profile, facts));
    if ("劳动纠纷".equals(profile.caseType())) risks.add("劳动争议通常存在仲裁前置和时效问题，建议尽早核对关键日期。");
    if ("民间借贷纠纷".equals(profile.caseType()) && !bool(facts, "borrowerIdentity")) risks.add("缺少对方身份或送达信息会影响起诉和执行。");
    if (containsAny(String.valueOf(facts.get("rawDescription")), "刑事", "报警", "行政处罚", "婚姻", "继承", "金额巨大")) {
      risks.add("该问题可能属于高风险或复杂案件，建议尽快咨询专业律师。");
    }
    return risks;
  }

  private List<String> amountRisks(CaseProfile profile, Map<String, Object> facts) {
    List<String> risks = new ArrayList<>();
    if ("劳动纠纷".equals(profile.caseType()) && bool(facts, "salary") && !bool(facts, "hasClaimAmount")) {
      risks.add("欠薪请求金额尚未核算清楚，仲裁请求过高或过低都可能影响维权效率。");
    }
    if ("房屋租赁纠纷".equals(profile.caseType()) && bool(facts, "depositAmount") && !bool(facts, "withholdingReason")) {
      risks.add("押金争议缺少扣款依据和金额明细，暂不宜直接认定房东需全额返还。");
    }
    if ("民间借贷纠纷".equals(profile.caseType()) && bool(facts, "loanAmount") && !bool(facts, "partialRepayment")) {
      risks.add("未区分借款本金、已还金额和未还余额，起诉请求金额可能需要进一步核算。");
    }
    if ("消费维权纠纷".equals(profile.caseType()) && bool(facts, "payment") && !bool(facts, "lossAmountKnown")) {
      risks.add("退款或赔偿范围尚不明确，建议先固定订单金额、问题证据和售后处理结果。");
    }
    return risks;
  }

  private List<Map<String, Object>> claimReadiness(CaseProfile profile, Map<String, Object> facts) {
    List<Map<String, Object>> items = new ArrayList<>();
    boolean hasAdverse = !listFact(facts, "adverseFactors").isEmpty();
    boolean hasUrgent = !listFact(facts, "urgentFactors").isEmpty();
    switch (profile.caseType()) {
      case "劳动纠纷" -> {
        items.add(readiness("支付拖欠工资",
            bool(facts, "salary") && bool(facts, "unpaidPeriod") && bool(facts, "hasPaymentRecord") ? "ready" : "needs_evidence",
            "工资请求以工资标准、拖欠月份、支付记录和劳动关系为核心。",
            "补工资流水、工资条、欠薪月份表和劳动关系证据。"));
        if (!bool(facts, "hasWrittenContract")) {
          items.add(readiness("未签书面劳动合同责任",
              bool(facts, "employmentStart") && bool(facts, "salary") && bool(facts, "laborEvidence") ? "ready" : "needs_evidence",
              "二倍工资等请求需要证明入职时间、实际用工和未签书面合同状态。",
              "补入职材料、考勤、工作群、工资流水和是否补签合同的说明。"));
        }
        if (bool(facts, "termination")) {
          items.add(readiness("解除/辞退赔偿",
              hasAdverse ? "high_risk" : "needs_evidence",
              "解除类请求取决于解除原因、通知程序、工作年限和工资标准。",
              "补辞退通知、解除理由、聊天/录音、规章制度依据和工作年限。"));
        }
      }
      case "房屋租赁纠纷" -> {
        items.add(readiness("返还押金/租金",
            hasAdverse || bool(facts, "withholdingReason") ? "high_risk" : bool(facts, "leaseContract") && bool(facts, "depositAmount") && bool(facts, "handover") ? "ready" : "needs_evidence",
            "押金返还取决于合同约定、押金金额、交接状态和扣款依据。",
            "补租赁合同、押金凭证、交接照片/清单、扣款明细和沟通记录。"));
        items.add(readiness("维修/违约责任",
            bool(facts, "leaseContract") && bool(facts, "communication") && bool(facts, "hasPhotoOrVideo") ? "ready" : "needs_evidence",
            "维修或违约责任需要证明损坏原因、通知记录、合同约定和损失金额。",
            "补房屋问题照片、维修报价/发票、通知记录和合同条款。"));
      }
      case "民间借贷纠纷" -> {
        items.add(readiness("返还借款本金",
            hasAdverse || hasUrgent ? "high_risk" : bool(facts, "loanAmount") && bool(facts, "delivery") && bool(facts, "loanAgreement") && bool(facts, "repaymentDue") && bool(facts, "borrowerIdentity") ? "ready" : "needs_evidence",
            "本金请求需要证明借贷合意、款项交付、到期未还和对方身份。",
            "补转账流水、聊天承诺、催款记录、已还款明细和身份/地址信息。"));
        items.add(readiness("主张利息或逾期利息",
            bool(facts, "interestAgreement") && bool(facts, "repaymentDue") ? "ready" : "needs_evidence",
            "利息请求需要有约定、起算时间和合法范围。",
            "补利息约定、还款期限、逾期起算点和计算表。"));
      }
      case "消费维权纠纷" -> {
        items.add(readiness("退款/退货/维修",
            bool(facts, "order") && bool(facts, "payment") && bool(facts, "afterSaleRecord") && (bool(facts, "defect") || bool(facts, "merchantPromise")) ? "ready" : "needs_evidence",
            "退款或维修需要证明交易关系、问题事实、商家承诺和售后记录。",
            "补订单、支付凭证、问题照片/检测、宣传截图和售后沟通。"));
        items.add(readiness("赔偿损失",
            bool(facts, "lossAmountKnown") && bool(facts, "defect") ? "ready" : "needs_evidence",
            "赔偿请求需要说明损失范围、因果关系和金额计算。",
            "补损失清单、维修/检测费用、退款差额和平台处理结果。"));
      }
      default -> items.add(readiness("明确可主张请求", "needs_evidence", "当前法律关系未完全明确。", "先补双方身份、合同/交易凭证、金额、时间线和沟通记录。"));
    }
    return items;
  }

  private Map<String, Object> readiness(String claim, String status, String reason, String nextEvidence) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("claim", claim);
    item.put("status", status);
    item.put("reason", reason);
    item.put("nextEvidence", nextEvidence);
    return item;
  }

  private String conclusionLevel(double completenessScore, List<IssueAnalysis> issues, Map<String, Object> facts) {
    long strong = issues.stream().filter(issue -> "strong".equals(issue.evidenceStrength())).count();
    if (completenessScore < 0.45) return "needs_more_facts";
    if (bool(facts, "classificationAmbiguous")) return "preliminary_possible";
    if (!listFact(facts, "urgentFactors").isEmpty()) return "preliminary_possible";
    if (!listFact(facts, "adverseFactors").isEmpty()) return "preliminary_possible";
    if (strong > 0 && completenessScore >= 0.75) return "preliminary_supported";
    return "preliminary_possible";
  }

  private String buildReply(CaseProfile profile, double completenessScore, String conclusionLevel, List<String> missingQuestions,
                            List<IssueAnalysis> issues, List<EvidenceAssessment> evidenceAssessments, List<String> actionPath,
                            List<String> risks, List<Citation> citations) {
    StringBuilder sb = new StringBuilder();
    sb.append(disclaimer()).append("\n\n");
    sb.append("案件识别：").append(profile.caseType()).append(" / ").append(profile.subType()).append("\n");
    sb.append("诉求目标：").append(String.join("、", profile.claimGoals())).append("\n");
    sb.append("事实完整度：").append(Math.round(completenessScore * 100)).append("%").append("（").append(conclusionLevel).append("）\n\n");

    if (!missingQuestions.isEmpty()) {
      sb.append("优先追问：\n");
      missingQuestions.forEach(q -> sb.append("- ").append(q).append("\n"));
      sb.append("\n");
    }

    sb.append("争点分析：\n");
    for (IssueAnalysis issue : issues) {
      sb.append("- 【").append(issue.issue()).append("】").append(issue.application()).append(" ");
      sb.append(issue.conclusion()).append(" 已知：").append(emptyAsNone(issue.knownFacts()));
      sb.append("；缺失：").append(emptyAsNone(issue.missingFacts())).append("。\n");
    }

    sb.append("\n证据强度：\n");
    for (EvidenceAssessment evidence : evidenceAssessments) {
      sb.append("- ").append(evidence.name()).append("：").append(evidence.purpose());
      sb.append(evidence.provided() ? "（已出现线索）" : "（建议补充）").append("\n");
    }

    sb.append("\n建议路径：\n");
    actionPath.forEach(step -> sb.append("- ").append(step).append("\n"));

    sb.append("\n风险提示：\n");
    risks.forEach(risk -> sb.append("- ").append(risk).append("\n"));

    if (!citations.isEmpty()) {
      sb.append("\n参考来源：\n");
      citations.forEach(citation -> sb.append("- ").append(citation.title()).append("：").append(citation.sourceUrl()).append("\n"));
    }
    return sb.toString();
  }

  private String disclaimer() {
    return properties.disclaimer() == null || properties.disclaimer().isBlank()
        ? "仅供初步参考，不构成正式法律意见；复杂或高风险案件建议咨询专业律师。"
        : properties.disclaimer();
  }

  private List<String> findMatches(Pattern pattern, String text) {
    List<String> values = new ArrayList<>();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) values.add(matcher.group(1).trim());
    return values.stream().distinct().toList();
  }

  private boolean hasAmount(String text) {
    return AMOUNT_PATTERN.matcher(text).find();
  }

  private boolean hasDate(String text) {
    return DATE_PATTERN.matcher(text).find();
  }

  private boolean hasJurisdictionInfo(String text) {
    return !findMatches(LOCATION_PATTERN, text).isEmpty()
        || containsAny(text, "工作地", "公司所在地", "单位所在地", "劳动合同履行地", "房屋所在地", "租房地址",
        "被告住所地", "户籍地", "经常居住地", "合同履行地", "收货地", "商家所在地", "平台所在地");
  }

  private String jurisdictionFocus(CaseProfile profile) {
    return switch (profile.caseType()) {
      case "劳动纠纷" -> "请补充实际工作地、用人单位注册地/办公地，以及劳动合同约定的履行地，用于判断劳动仲裁管辖。";
      case "房屋租赁纠纷" -> "请补充租赁房屋所在地、房东/中介身份地址，以及合同约定的争议解决地点。";
      case "民间借贷纠纷" -> "请补充对方住所地/经常居住地、借款交付地、约定还款地，用于判断起诉或支付令管辖。";
      case "消费维权纠纷" -> "请补充商家名称和所在地、平台名称、收货地或服务履行地，用于判断平台投诉、监管投诉或诉讼地点。";
      default -> "请补充对方所在地、合同履行地、付款/收货/服务发生地等管辖线索。";
    };
  }

  private boolean hasKeyDate(String text) {
    return hasDate(text) || containsAny(text,
        "今天", "昨天", "前天", "上周", "本周", "上个月", "这个月", "本月", "去年", "今年",
        "辞退当天", "还款日", "购买时间", "收货当天", "发现问题当天");
  }

  private String timelineFocus(CaseProfile profile, String text, Map<String, Object> facts) {
    return switch (profile.caseType()) {
      case "劳动纠纷" -> "请补充入职时间、欠薪起止月份、离职/被辞退时间、最近一次催要工资或公司回应的时间。";
      case "房屋租赁纠纷" -> "请补充租期起止、退租/交接日期、房东扣款或拒退押金日期，以及你通知对方的时间。";
      case "民间借贷纠纷" -> "请补充借款交付日期、约定还款日、最近一次催款时间，以及对方最近一次承认欠款的时间。";
      case "消费维权纠纷" -> "请补充购买/收货时间、发现问题时间、首次申请售后或退款时间，以及平台/商家处理时间。";
      default -> "请按时间顺序补充事件发生、付款、沟通、催告和对方回应的关键日期。";
    };
  }

  private List<String> caseTypeCandidates(String text) {
    return List.of(laborProfile(text), leaseProfile(text), loanProfile(text), consumerProfile(text)).stream()
        .filter(profile -> profile.score() > 0)
        .sorted(Comparator.comparingInt(CaseProfile::score).reversed())
        .limit(3)
        .map(profile -> profile.caseType() + "(" + profile.score() + ")")
        .toList();
  }

  private boolean classificationAmbiguous(String text) {
    List<CaseProfile> candidates = List.of(laborProfile(text), leaseProfile(text), loanProfile(text), consumerProfile(text)).stream()
        .filter(profile -> profile.score() > 0)
        .sorted(Comparator.comparingInt(CaseProfile::score).reversed())
        .toList();
    if (candidates.size() < 2) return false;
    int top = candidates.get(0).score();
    int second = candidates.get(1).score();
    return top >= 2 && second >= 2 && top - second <= 1;
  }

  private String procedureFit(CaseProfile profile, Map<String, Object> facts) {
    boolean hasAdverse = !listFact(facts, "adverseFactors").isEmpty();
    return switch (profile.caseType()) {
      case "劳动纠纷" -> {
        if (bool(facts, "termination") || hasAdverse) {
          yield "涉及解除、赔偿或对方抗辩时，劳动仲裁通常比单纯投诉更匹配；投诉可作为辅助固定欠薪线索。";
        }
        if (bool(facts, "salary") && bool(facts, "hasPaymentRecord")) {
          yield "若主要是欠薪且工资流水较清楚，可先向劳动监察投诉；对方不处理或涉及二倍工资/赔偿时再准备仲裁。";
        }
        yield "劳动关系和欠薪证据还不完整，先补齐用工管理、工资和时间线，再判断投诉或仲裁优先级。";
      }
      case "民间借贷纠纷" -> {
        if (hasAdverse) {
          yield "对方已经出现抗辩，支付令被异议的可能较高，更适合准备普通民事诉讼和反证材料。";
        }
        if (bool(facts, "borrowerIdentity") && bool(facts, "loanAmount") && bool(facts, "delivery") && bool(facts, "repaymentDue")) {
          yield "债权金额、到期时间和送达信息较清楚，可评估支付令；若预计对方异议或事实争议较大，则转普通民事诉讼。";
        }
        if (!bool(facts, "borrowerIdentity")) {
          yield "对方身份或送达信息不足，先补姓名、手机号、住址或身份证线索，否则起诉和执行都会受影响。";
        }
        yield "先补借贷合意、交付、到期和身份信息，再选择支付令或普通诉讼。";
      }
      case "房屋租赁纠纷" -> {
        if (hasAdverse || bool(facts, "withholdingReason")) {
          yield "存在扣款或损坏争议时，先书面要求房东提供扣款明细；协商不成更适合调解或小额民事诉讼。";
        }
        if (bool(facts, "leaseContract") && bool(facts, "handover") && bool(facts, "depositAmount")) {
          yield "合同、押金和交接证据较完整，可先书面催告返还；拒不返还时考虑调解或民事诉讼。";
        }
        yield "先补合同条款、押金凭证和交接状态，再判断是否直接诉讼。";
      }
      case "消费维权纠纷" -> {
        if (bool(facts, "afterSaleRecord") && bool(facts, "order")) {
          yield "已有订单和售后记录，可先走平台规则并同步 12315/市场监管投诉；金额较大或平台处理失败时再评估诉讼。";
        }
        if (hasAdverse) {
          yield "商家已有抗辩时，先固定宣传页面、售后规则和问题证据，再决定投诉或诉讼。";
        }
        yield "先补订单、支付、问题证据和首次售后记录，优先平台售后，再考虑 12315 或诉讼。";
      }
      default -> "先明确法律关系、请求金额、对方身份和证据基础，再选择投诉、调解、支付令或诉讼路径。";
    };
  }

  private List<String> detectAdverseFactors(CaseProfile profile, String text) {
    List<String> factors = new ArrayList<>();
    switch (profile.caseType()) {
      case "劳动纠纷" -> {
        if (containsAny(text, "公司说已结清", "工资已结清", "结清工资", "已经发完")) {
          factors.add("用人单位可能主张工资已经结清，需要用工资流水、工资条、欠薪明细反证。");
        }
        if (containsAny(text, "自愿离职", "主动离职", "自己辞职")) {
          factors.add("用人单位可能主张系员工主动离职，解除/赔偿请求需补强辞退通知或沟通记录。");
        }
        if (containsAny(text, "劳务关系", "承揽", "外包", "兼职")) {
          factors.add("用人单位可能否认劳动关系，主张劳务、承揽或外包关系，需要补强用工管理证据。");
        }
      }
      case "房屋租赁纠纷" -> {
        if (containsAny(text, "房东说损坏", "损坏家具", "墙面损坏", "扣维修费", "扣清洁费")) {
          factors.add("房东可能以房屋或家具损坏、维修/清洁费用为由扣押金，需要交接照片、维修票据和扣款明细。");
        }
        if (containsAny(text, "提前退租", "违约金", "没到期退租")) {
          factors.add("对方可能主张提前退租违约金，需要核对合同条款、通知时间和房屋再出租情况。");
        }
      }
      case "民间借贷纠纷" -> {
        if (containsAny(text, "对方说是赠与", "说是赠与", "不是借款", "投资款", "合伙款")) {
          factors.add("对方可能否认借贷合意，主张赠与、投资或合伙款，需要聊天承诺、备注、催款回应等证明借款性质。");
        }
        if (containsAny(text, "已经还清", "已还清", "还完了", "全部还了")) {
          factors.add("对方可能主张已经清偿，需要逐笔核对本金、已还款和未还余额。");
        }
      }
      case "消费维权纠纷" -> {
        if (containsAny(text, "商家说已告知", "页面写了", "不支持退款", "超过售后", "人为损坏")) {
          factors.add("商家可能以已充分告知、超过售后期限或人为损坏抗辩，需要保存宣传页面、售后规则、检测或使用记录。");
        }
      }
      default -> {
        if (containsAny(text, "对方不同意", "对方否认", "对方说")) {
          factors.add("对方已经出现不同说法，需要围绕争议点准备直接证据。");
        }
      }
    }
    return factors.stream().distinct().toList();
  }

  private List<String> detectUrgentFactors(CaseProfile profile, String text) {
    List<String> factors = new ArrayList<>();
    if (containsAny(text, "马上到期", "快到期", "即将到期", "最后一天", "超过一年", "超过三年", "拖了很久")) {
      factors.add("可能存在时效或程序期限压力，应立即核对关键日期并保留催告记录。");
    }
    if (containsAny(text, "失联", "联系不上", "拉黑", "跑路", "注销", "关店", "搬走")) {
      factors.add("对方可能失联或主体状态不稳定，应优先固定身份、地址、营业信息和送达线索。");
    }
    if (containsAny(text, "转移财产", "卖房", "卖车", "转账转走", "财产转移", "逃避执行")) {
      factors.add("可能存在财产转移风险，证据较明确且金额较大时可咨询是否申请财产保全。");
    }
    if (containsAny(text, "删除聊天", "删记录", "撤回消息", "监控要覆盖", "证据快没了", "网页下架", "商品下架")) {
      factors.add("存在证据灭失风险，应立即备份原始聊天、网页、订单、监控、照片或申请公证/平台留痕。");
    }
    if ("劳动纠纷".equals(profile.caseType()) && containsAny(text, "公司注销", "公司倒闭", "老板跑路", "裁员很多")) {
      factors.add("用人单位经营状态可能恶化，应尽快核对主体信息、欠薪证据和仲裁/投诉窗口。");
    }
    if ("民间借贷纠纷".equals(profile.caseType()) && containsAny(text, "金额很大", "几十万", "上百万", "唯一财产")) {
      factors.add("借贷金额或执行风险较高，建议尽快整理证据并咨询诉讼、保全和执行可行性。");
    }
    return factors.stream().distinct().toList();
  }

  private boolean containsPositiveDocument(String text) {
    boolean hasDocumentWord = containsAny(text, "合同", "协议", "借条", "欠条");
    boolean negated = containsAny(text, "未签", "没签", "没有签", "无合同", "没有合同", "没有借条", "没借条", "无借条", "没有欠条");
    return hasDocumentWord && !negated;
  }

  @SuppressWarnings("unchecked")
  private List<String> listFact(Map<String, Object> facts, String key) {
    Object value = facts.get(key);
    if (value instanceof List<?> list) {
      return (List<String>) list;
    }
    return List.of();
  }

  private List<String> detectContradictions(String text) {
    List<String> contradictions = new ArrayList<>();
    boolean deniesLaborContract = containsAny(text, "未签劳动合同", "没签劳动合同", "没有签劳动合同", "无劳动合同", "没有合同");
    boolean affirmsLaborContract = containsAny(text, "有劳动合同", "签了劳动合同", "签过劳动合同", "劳动合同照片", "合同照片", "合同原件", "合同扫描件");
    if (deniesLaborContract && affirmsLaborContract) {
      contradictions.add("关于是否签过书面劳动合同的陈述前后不一致，请确认是从未签署、后来补签，还是找到了合同照片/复印件。");
    }

    boolean deniesLoanNote = containsAny(text, "没有借条", "没借条", "无借条", "没有欠条", "无欠条");
    boolean affirmsLoanNote = containsAny(text, "有借条", "有欠条", "借条照片", "欠条照片", "借条原件", "欠条原件");
    if (deniesLoanNote && affirmsLoanNote) {
      contradictions.add("关于是否存在借条/欠条的陈述前后不一致，请确认是否有原件、照片或仅有聊天承诺。");
    }

    boolean saysRepaid = containsAny(text, "已经还清", "已还清", "还完了", "全部还了");
    boolean saysUnpaid = containsAny(text, "不还", "没还", "未还", "欠钱", "欠款");
    if (saysRepaid && saysUnpaid) {
      contradictions.add("关于款项是否已经还清存在冲突，请列明借款本金、已还金额、未还余额和对应凭证。");
    }
    return contradictions;
  }

  private boolean bool(Map<String, Object> facts, String key) {
    Object value = facts.get(key);
    if (value instanceof Boolean b) return b;
    if (value instanceof List<?> list) return !list.isEmpty();
    return value != null && !String.valueOf(value).isBlank();
  }

  private int score(String text, String... keys) {
    int score = 0;
    String lower = text.toLowerCase(Locale.ROOT);
    for (String key : keys) {
      if (lower.contains(key.toLowerCase(Locale.ROOT))) score++;
    }
    return score;
  }

  private boolean containsAny(String text, String... keys) {
    for (String key : keys) {
      if (text.contains(key)) return true;
    }
    return false;
  }

  private String matchedSubTypes(String text, List<SubtypeRule> rules, String fallback) {
    List<String> matched = rules.stream()
        .filter(rule -> rule.keywords().stream().anyMatch(text::contains))
        .map(SubtypeRule::name)
        .distinct()
        .limit(3)
        .toList();
    return matched.isEmpty() ? fallback : String.join(" + ", matched);
  }

  private SubtypeRule rule(String name, String... keywords) {
    return new SubtypeRule(name, List.of(keywords));
  }

  private List<String> goalsFor(String subType, String... defaults) {
    List<String> goals = new ArrayList<>(List.of(defaults));
    if (subType.contains("工资")) goals.add("核算欠薪金额");
    if (subType.contains("合同")) goals.add("评估未签书面合同责任");
    if (subType.contains("押金")) goals.add("主张返还押金");
    if (subType.contains("借")) goals.add("证明借贷合意和款项交付");
    if (subType.contains("退款")) goals.add("主张退款或赔偿");
    return goals.stream().distinct().toList();
  }

  private List<String> factKeys(String... keys) {
    return List.of(keys);
  }

  private EvidenceRule evidence(String name, String purpose, String strength, String... keywords) {
    return new EvidenceRule(name, purpose, strength, List.of(keywords));
  }

  private String strength(int known, int total) {
    if (total == 0) return "weak";
    double ratio = known * 1.0 / total;
    if (ratio >= 0.75) return "strong";
    if (ratio >= 0.45) return "medium";
    return "weak";
  }

  private String emptyAsNone(List<String> values) {
    return values == null || values.isEmpty() ? "暂无" : String.join("、", values);
  }

  private record CaseProfile(
      String caseType,
      String subType,
      int score,
      List<String> claimGoals,
      List<String> requiredFactKeys,
      List<EvidenceRule> evidenceRules
  ) {
  }

  private record EvidenceRule(String name, String purpose, String strength, List<String> keywords) {
  }

  private record SubtypeRule(String name, List<String> keywords) {
  }
}
