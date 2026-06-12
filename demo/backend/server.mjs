import http from 'node:http'
import { URL } from 'node:url'

const sessions = []
const messages = new Map()
const analyses = new Map()
const reports = new Map()
let nextId = 1
const DEMO_VERSION = 'rule-agent-2026-06-04'
const LLM_PROVIDER = process.env.LLM_PROVIDER || 'none'
const LLM_API_KEY = process.env.LLM_API_KEY || ''
const LLM_BASE_URL = (process.env.LLM_BASE_URL || providerBaseUrl(LLM_PROVIDER)).replace(/\/$/, '')
const LLM_MODEL = process.env.LLM_MODEL || providerModel(LLM_PROVIDER)
const LLM_ENABLED = Boolean(LLM_API_KEY && LLM_PROVIDER !== 'none' && LLM_PROVIDER !== 'mock')
const LLM_TIMEOUT_MS = Number(process.env.LLM_TIMEOUT_MS || 45000)
const PORT = Number(process.env.PORT || 8080)

function providerBaseUrl(provider) {
  const normalized = (provider || '').toLowerCase()
  if (normalized === 'deepseek') return 'https://api.deepseek.com'
  if (normalized === 'qwen' || normalized === 'dashscope' || normalized === 'tongyi') return 'https://dashscope.aliyuncs.com/compatible-mode/v1'
  return 'https://api.openai.com/v1'
}

function providerModel(provider) {
  const normalized = (provider || '').toLowerCase()
  if (normalized === 'deepseek') return 'deepseek-chat'
  if (normalized === 'qwen' || normalized === 'dashscope' || normalized === 'tongyi') return 'qwen-plus'
  return 'gpt-4o-mini'
}

function safeJsonParse(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
    if (fenced) return safeJsonParse(fenced[1])
    const start = text.indexOf('{')
    const end = text.lastIndexOf('}')
    if (start >= 0 && end > start) return safeJsonParse(text.slice(start, end + 1))
    return null
  }
}

function compactAnalysisForLlm(analysis) {
  return {
    caseType: analysis.caseType,
    subType: analysis.subType,
    claimGoals: analysis.claimGoals,
    completenessScore: analysis.completenessScore,
    conclusionLevel: analysis.conclusionLevel,
    facts: safeJsonParse(analysis.factsJson) || {},
    missingQuestions: safeJsonParse(analysis.missingQuestionsJson) || [],
    issues: safeJsonParse(analysis.issuesJson) || [],
    evidenceAssessments: safeJsonParse(analysis.evidenceAssessmentsJson) || [],
    actionPath: safeJsonParse(analysis.actionPathJson) || [],
    risks: safeJsonParse(analysis.risksJson) || [],
    citations: safeJsonParse(analysis.citationsJson) || []
  }
}

function buildMultiAgentPrompt(userFacts, draft) {
  return {
    system: [
      'You are a Chinese legal-rights preliminary analysis multi-agent team.',
      'Hard boundaries: every output is only preliminary reference; do not pretend to be a lawyer, court, or authority; do not promise win rate; do not instruct evidence fabrication, concealment, or evasion of liability.',
      'Work as these virtual agents: IntakeClassifier, FactExtractor, IssueSpotter, EvidenceAuditor, ProcedureStrategist, RiskReviewer, FinalComposer.',
      'Return JSON only. Do not wrap in markdown.',
      'Use cautious, source-aware Chinese. If facts are insufficient, ask targeted questions before strong conclusions.'
    ].join('\n'),
    user: JSON.stringify({
      task: 'Review and enhance the rule-engine draft. Keep useful draft fields, correct obvious mistakes, add precise issue analysis, evidence gaps, action path, and risk warnings.',
      userFacts,
      draft,
      requiredJsonShape: {
        caseType: 'string',
        subType: 'string',
        conclusionLevel: 'needs_more_facts | preliminary_possible | preliminary_supported',
        missingQuestions: ['string'],
        issues: [{
          issue: 'string',
          requiredFacts: ['string'],
          knownFacts: ['string'],
          missingFacts: ['string'],
          evidenceGaps: [{ fact: 'string', priority: 'high | medium | low', suggestion: 'string' }],
          evidenceStrength: 'weak | medium | strong',
          legalRule: 'string',
          application: 'string',
          conclusion: 'string'
        }],
        evidenceAssessments: [{ name: 'string', purpose: 'string', strength: 'weak | medium | strong', provided: true }],
        actionPath: ['string'],
        riskWarnings: ['string'],
        citations: [{ type: 'article | case | general', title: 'string', sourceUrl: 'string' }],
        userReply: 'string'
      }
    })
  }
}

async function callOpenAiCompatible(messages) {
  if (!LLM_ENABLED) return null
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), LLM_TIMEOUT_MS)
  try {
    const response = await fetch(`${LLM_BASE_URL}/chat/completions`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'content-type': 'application/json',
        authorization: `Bearer ${LLM_API_KEY}`
      },
      body: JSON.stringify({
        model: LLM_MODEL,
        temperature: 0.2,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: messages.system },
          { role: 'user', content: messages.user }
        ]
      })
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(payload.error?.message || `LLM HTTP ${response.status}`)
    }
    return payload.choices?.[0]?.message?.content || ''
  } finally {
    clearTimeout(timer)
  }
}

function applyLlmEnhancement(base, enhancement) {
  if (!enhancement || typeof enhancement !== 'object') return base
  const merged = { ...base }
  const fields = [
    ['caseType', 'caseType'],
    ['subType', 'subType'],
    ['conclusionLevel', 'conclusionLevel'],
    ['missingQuestions', 'missingQuestionsJson'],
    ['issues', 'issuesJson'],
    ['evidenceAssessments', 'evidenceAssessmentsJson'],
    ['actionPath', 'actionPathJson'],
    ['riskWarnings', 'risksJson'],
    ['citations', 'citationsJson']
  ]
  for (const [source, target] of fields) {
    if (enhancement[source] !== undefined) {
      merged[target] = Array.isArray(enhancement[source]) || typeof enhancement[source] === 'object'
        ? JSON.stringify(enhancement[source])
        : enhancement[source]
    }
  }
  if (Array.isArray(enhancement.evidenceAssessments)) {
    merged.evidenceJson = JSON.stringify(enhancement.evidenceAssessments.map((item) => `${item.name}: ${item.purpose}${item.provided ? '（已出现线索）' : '（建议补充）'}`))
  }
  if (typeof enhancement.caseType === 'string') merged.caseType = enhancement.caseType
  if (typeof enhancement.subType === 'string') merged.subType = enhancement.subType
  if (typeof enhancement.conclusionLevel === 'string') merged.conclusionLevel = enhancement.conclusionLevel
  if (typeof enhancement.userReply === 'string' && enhancement.userReply.trim()) {
    merged.reply = enhancement.userReply.includes('仅供初步参考')
      ? enhancement.userReply
      : `仅供初步参考，不构成正式法律意见；复杂或高风险案件建议咨询专业律师。\n\n${enhancement.userReply}`
  }
  return merged
}

function writeSse(res, event, data) {
  const payload = typeof data === 'string' ? data : JSON.stringify(data)
  for (const line of payload.split('\n')) {
    res.write(`event: ${event}\ndata: ${line}\n\n`)
  }
}

function buildStreamingReplyPrompt(userFacts, draft) {
  const shortQuestion = isShortUserQuestion(userFacts)
  return {
    system: [
      'You are a Chinese legal-rights preliminary analysis multi-agent team.',
      'Virtual roles: Case Intake, Fact Extraction, Issue Spotting, Evidence Audit, Procedure Strategy, Safety Review, Final Composer.',
      'Output only the user-facing Chinese answer. Do not output JSON.',
      'Do not use Markdown syntax. Do not use ### headings, **bold**, tables, blockquotes, code fences, or pipe characters.',
      'Chat answer policy: answer the user question first, briefly. Do not write a full report in the chat window.',
      'Use at most 4 short sections: 初步结论, 为什么, 还需确认, 下一步.',
      'Maximum 450 Chinese characters for ordinary questions. Use at most 5 bullets total.',
      'Do not include long evidence tables, long legal explanations, or exhaustive checklists. Detailed analysis belongs to the side panel/report.',
      'Start with: 仅供初步参考，不构成正式法律意见；复杂或高风险案件建议咨询专业律师。',
      'Be concrete, cautious, and structured. If facts are insufficient, prioritize targeted questions.',
      'Do not promise winning, do not impersonate a lawyer/court/authority, and do not advise fabricating or hiding evidence.'
    ].join('\n'),
    user: JSON.stringify({
      task: shortQuestion
        ? '用户只问了一个简短问题。请直接短答，不要展开成完整案件报告。'
        : '基于用户事实和规则 Agent 草案，生成简洁、结论优先、可执行的初步回复。',
      responseMode: shortQuestion ? 'brief_answer' : 'concise_chat_answer',
      strictLengthLimit: shortQuestion ? '250 Chinese characters, at most 3 bullets' : '450 Chinese characters, at most 5 bullets',
      userFacts,
      ruleDraft: draft,
      answerSections: [
        '初步结论',
        '为什么',
        '还需确认',
        '下一步'
      ]
    })
  }
}

function isShortUserQuestion(userFacts) {
  const last = String(userFacts || '').split('\n').filter(Boolean).pop() || ''
  const text = last.replace(/^第\d+次用户陈述：/, '').trim()
  return text.length <= 45 || /吗[？?]?$|怎么办[？?]?$|有没有|是否|谁负责|责任/.test(text)
}

async function streamOpenAiCompatibleReply(messages, onChunk) {
  if (!LLM_ENABLED) return ''
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), LLM_TIMEOUT_MS)
  let fullText = ''
  try {
    const response = await fetch(`${LLM_BASE_URL}/chat/completions`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'content-type': 'application/json',
        authorization: `Bearer ${LLM_API_KEY}`
      },
      body: JSON.stringify({
        model: LLM_MODEL,
        temperature: 0.2,
        stream: true,
        messages: [
          { role: 'system', content: messages.system },
          { role: 'user', content: messages.user }
        ]
      })
    })
    if (!response.ok) {
      const errorText = await response.text().catch(() => '')
      throw new Error(errorText || `LLM HTTP ${response.status}`)
    }
    const decoder = new TextDecoder()
    let buffer = ''
    for await (const chunk of response.body) {
      buffer += decoder.decode(chunk, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const part of parts) {
        for (const rawLine of part.split('\n')) {
          const line = rawLine.trim()
          if (!line.startsWith('data:')) continue
          const data = line.slice(5).trim()
          if (!data || data === '[DONE]') continue
          const payload = safeJsonParse(data)
          const delta = payload?.choices?.[0]?.delta?.content || ''
          if (delta) {
            fullText += delta
            onChunk(delta)
          }
        }
      }
    }
    return fullText
  } finally {
    clearTimeout(timer)
  }
}

async function enhanceAnalysisWithLlm(baseAnalysis, userFacts) {
  if (!LLM_ENABLED) return baseAnalysis
  try {
    const prompt = buildMultiAgentPrompt(userFacts, compactAnalysisForLlm(baseAnalysis))
    const content = await callOpenAiCompatible(prompt)
    const enhancement = safeJsonParse(content)
    return applyLlmEnhancement(baseAnalysis, enhancement)
  } catch (error) {
    const risks = safeJsonParse(baseAnalysis.risksJson) || []
    risks.unshift(`LLM 增强失败，已回退到规则 Agent：${error.message}`)
    return { ...baseAnalysis, risksJson: JSON.stringify(risks) }
  }
}

function json(res, data, status = 200) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,DELETE,OPTIONS'
  })
  res.end(JSON.stringify({ code: status === 200 ? 0 : status, message: status === 200 ? 'ok' : String(data), data: status === 200 ? data : null }))
}

function fail(res, message, status = 400) {
  return json(res, message, status)
}

function readBody(req) {
  return new Promise((resolve) => {
    let body = ''
    req.on('data', (chunk) => (body += chunk))
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {})
      } catch {
        resolve({})
      }
    })
  })
}

const includesAny = (text, words) => words.some((word) => text.includes(word))
const amountPattern = /((?:人民币|¥)\s*\d+(?:\.\d+)?\s*(?:元|万元|万|块)?|\d+(?:\.\d+)?\s*(?:万元|元|万|块))/g
const locationPattern = /([\u4e00-\u9fa5]{2,}(?:省|市|区|县|镇|街道|路|小区|公司|平台))/g
const hasAmount = (text) => {
  amountPattern.lastIndex = 0
  return amountPattern.test(text)
}
const findAmounts = (text) => {
  amountPattern.lastIndex = 0
  return [...new Set([...text.matchAll(amountPattern)].map((m) => m[1]))]
}
const hasDate = (text) => /(\d{4}年\d{1,2}月\d{0,2}日?|\d{1,2}月\d{1,2}日|\d+个?月|\d+年)/.test(text)
const hasKeyDate = (text) => hasDate(text) || includesAny(text, ['今天', '昨天', '前天', '上周', '本周', '上个月', '这个月', '本月', '去年', '今年', '辞退当天', '还款日', '购买时间', '收货当天', '发现问题当天'])
const findLocations = (text) => [...new Set([...text.matchAll(locationPattern)].map((m) => m[1]))]
const hasJurisdictionInfo = (text) => findLocations(text).length > 0 || includesAny(text, ['工作地', '公司所在地', '单位所在地', '劳动合同履行地', '房屋所在地', '租房地址', '被告住所地', '户籍地', '经常居住地', '合同履行地', '收货地', '商家所在地', '平台所在地'])

function timelineFocus(profile) {
  if (profile.caseType === '劳动纠纷') return '请补充入职时间、欠薪起止月份、离职/被辞退时间、最近一次催要工资或公司回应的时间。'
  if (profile.caseType === '房屋租赁纠纷') return '请补充租期起止、退租/交接日期、房东扣款或拒退押金日期，以及你通知对方的时间。'
  if (profile.caseType === '民间借贷纠纷') return '请补充借款交付日期、约定还款日、最近一次催款时间，以及对方最近一次承认欠款的时间。'
  if (profile.caseType === '消费维权纠纷') return '请补充购买/收货时间、发现问题时间、首次申请售后或退款时间，以及平台/商家处理时间。'
  return '请按时间顺序补充事件发生、付款、沟通、催告和对方回应的关键日期。'
}

function jurisdictionFocus(profile) {
  if (profile.caseType === '劳动纠纷') return '请补充实际工作地、用人单位注册地/办公地，以及劳动合同约定的履行地，用于判断劳动仲裁管辖。'
  if (profile.caseType === '房屋租赁纠纷') return '请补充租赁房屋所在地、房东/中介身份地址，以及合同约定的争议解决地点。'
  if (profile.caseType === '民间借贷纠纷') return '请补充对方住所地/经常居住地、借款交付地、约定还款地，用于判断起诉或支付令管辖。'
  if (profile.caseType === '消费维权纠纷') return '请补充商家名称和所在地、平台名称、收货地或服务履行地，用于判断平台投诉、监管投诉或诉讼地点。'
  return '请补充对方所在地、合同履行地、付款/收货/服务发生地等管辖线索。'
}

function detectAdverseFactors(profile, text) {
  const factors = []
  if (profile.caseType === '劳动纠纷') {
    if (includesAny(text, ['公司说已结清', '工资已结清', '结清工资', '已经发完'])) factors.push('用人单位可能主张工资已经结清，需要用工资流水、工资条、欠薪明细反证。')
    if (includesAny(text, ['自愿离职', '主动离职', '自己辞职'])) factors.push('用人单位可能主张系员工主动离职，解除/赔偿请求需补强辞退通知或沟通记录。')
    if (includesAny(text, ['劳务关系', '承揽', '外包', '兼职'])) factors.push('用人单位可能否认劳动关系，主张劳务、承揽或外包关系，需要补强用工管理证据。')
  } else if (profile.caseType === '房屋租赁纠纷') {
    if (includesAny(text, ['房东说损坏', '损坏家具', '墙面损坏', '扣维修费', '扣清洁费'])) factors.push('房东可能以房屋或家具损坏、维修/清洁费用为由扣押金，需要交接照片、维修票据和扣款明细。')
    if (includesAny(text, ['提前退租', '违约金', '没到期退租'])) factors.push('对方可能主张提前退租违约金，需要核对合同条款、通知时间和房屋再出租情况。')
  } else if (profile.caseType === '民间借贷纠纷') {
    if (includesAny(text, ['对方说是赠与', '说是赠与', '不是借款', '投资款', '合伙款'])) factors.push('对方可能否认借贷合意，主张赠与、投资或合伙款，需要聊天承诺、备注、催款回应等证明借款性质。')
    if (includesAny(text, ['已经还清', '已还清', '还完了', '全部还了'])) factors.push('对方可能主张已经清偿，需要逐笔核对本金、已还款和未还余额。')
  } else if (profile.caseType === '消费维权纠纷') {
    if (includesAny(text, ['商家说已告知', '页面写了', '不支持退款', '超过售后', '人为损坏'])) factors.push('商家可能以已充分告知、超过售后期限或人为损坏抗辩，需要保存宣传页面、售后规则、检测或使用记录。')
  } else if (includesAny(text, ['对方不同意', '对方否认', '对方说'])) {
    factors.push('对方已经出现不同说法，需要围绕争议点准备直接证据。')
  }
  return [...new Set(factors)]
}

function detectUrgentFactors(profile, text) {
  const factors = []
  if (includesAny(text, ['马上到期', '快到期', '即将到期', '最后一天', '超过一年', '超过三年', '拖了很久'])) factors.push('可能存在时效或程序期限压力，应立即核对关键日期并保留催告记录。')
  if (includesAny(text, ['失联', '联系不上', '拉黑', '跑路', '注销', '关店', '搬走'])) factors.push('对方可能失联或主体状态不稳定，应优先固定身份、地址、营业信息和送达线索。')
  if (includesAny(text, ['转移财产', '卖房', '卖车', '转账转走', '财产转移', '逃避执行'])) factors.push('可能存在财产转移风险，证据较明确且金额较大时可咨询是否申请财产保全。')
  if (includesAny(text, ['删除聊天', '删记录', '撤回消息', '监控要覆盖', '证据快没了', '网页下架', '商品下架'])) factors.push('存在证据灭失风险，应立即备份原始聊天、网页、订单、监控、照片或申请公证/平台留痕。')
  if (profile.caseType === '劳动纠纷' && includesAny(text, ['公司注销', '公司倒闭', '老板跑路', '裁员很多'])) factors.push('用人单位经营状态可能恶化，应尽快核对主体信息、欠薪证据和仲裁/投诉窗口。')
  if (profile.caseType === '民间借贷纠纷' && includesAny(text, ['金额很大', '几十万', '上百万', '唯一财产'])) factors.push('借贷金额或执行风险较高，建议尽快整理证据并咨询诉讼、保全和执行可行性。')
  return [...new Set(factors)]
}

function procedureFit(profile, facts) {
  const hasAdverse = Boolean(facts.adverseFactors?.length)
  if (profile.caseType === '劳动纠纷') {
    if (facts.termination || hasAdverse) return '涉及解除、赔偿或对方抗辩时，劳动仲裁通常比单纯投诉更匹配；投诉可作为辅助固定欠薪线索。'
    if (facts.salary && facts.hasPaymentRecord) return '若主要是欠薪且工资流水较清楚，可先向劳动监察投诉；对方不处理或涉及二倍工资/赔偿时再准备仲裁。'
    return '劳动关系和欠薪证据还不完整，先补齐用工管理、工资和时间线，再判断投诉或仲裁优先级。'
  }
  if (profile.caseType === '民间借贷纠纷') {
    if (hasAdverse) return '对方已经出现抗辩，支付令被异议的可能较高，更适合准备普通民事诉讼和反证材料。'
    if (facts.borrowerIdentity && facts.loanAmount && facts.delivery && facts.repaymentDue) return '债权金额、到期时间和送达信息较清楚，可评估支付令；若预计对方异议或事实争议较大，则转普通民事诉讼。'
    if (!facts.borrowerIdentity) return '对方身份或送达信息不足，先补姓名、手机号、住址或身份证线索，否则起诉和执行都会受影响。'
    return '先补借贷合意、交付、到期和身份信息，再选择支付令或普通诉讼。'
  }
  if (profile.caseType === '房屋租赁纠纷') {
    if (hasAdverse || facts.withholdingReason) return '存在扣款或损坏争议时，先书面要求房东提供扣款明细；协商不成更适合调解或小额民事诉讼。'
    if (facts.leaseContract && facts.handover && facts.depositAmount) return '合同、押金和交接证据较完整，可先书面催告返还；拒不返还时考虑调解或民事诉讼。'
    return '先补合同条款、押金凭证和交接状态，再判断是否直接诉讼。'
  }
  if (profile.caseType === '消费维权纠纷') {
    if (facts.afterSaleRecord && facts.order) return '已有订单和售后记录，可先走平台规则并同步 12315/市场监管投诉；金额较大或平台处理失败时再评估诉讼。'
    if (hasAdverse) return '商家已有抗辩时，先固定宣传页面、售后规则和问题证据，再决定投诉或诉讼。'
    return '先补订单、支付、问题证据和首次售后记录，优先平台售后，再考虑 12315 或诉讼。'
  }
  return '先明确法律关系、请求金额、对方身份和证据基础，再选择投诉、调解、支付令或诉讼路径。'
}

function countScore(text, words) {
  return words.reduce((sum, word) => sum + (text.includes(word) ? 1 : 0), 0)
}

function matchedSubTypes(text, rules, fallback) {
  const matched = []
  for (const [name, words] of rules) {
    if (includesAny(text, words) && !matched.includes(name)) matched.push(name)
  }
  return matched.length ? matched.slice(0, 3).join(' + ') : fallback
}

function profileFor(text) {
  const candidates = [
    {
      caseType: '劳动纠纷',
      score: countScore(text, ['工资', '欠薪', '劳动合同', '辞退', '开除', '工伤', '加班', '社保', '仲裁', '公司', '入职']),
      subType: matchedSubTypes(text, [
        ['工伤赔偿', ['工伤', '受伤', '职业病']],
        ['违法解除', ['辞退', '开除', '裁员', '解除']],
        ['未签劳动合同', ['未签', '没签', '没有签', '劳动合同']],
        ['拖欠工资', ['工资', '欠薪', '拖欠', '未发']]
      ], '劳动关系综合争议'),
      required: ['employmentStart', 'employer', 'salary', 'unpaidPeriod', 'laborEvidence'],
      goals: ['确认劳动关系', '支付工资/赔偿', '准备劳动仲裁']
    },
    {
      caseType: '房屋租赁纠纷',
      score: countScore(text, ['房东', '租房', '租赁', '押金', '租金', '退租', '维修', '中介', '房屋']),
      subType: matchedSubTypes(text, [
        ['押金返还', ['押金', '不退押金', '扣押金']],
        ['提前退租', ['提前退租', '退租', '违约金']],
        ['维修责任', ['维修', '漏水', '损坏', '修理']],
        ['租金违约', ['租金', '欠租', '涨租']]
      ], '房屋租赁综合争议'),
      required: ['leaseContract', 'depositAmount', 'leaseTerm', 'handover', 'communication'],
      goals: ['退还押金/租金', '确认违约责任', '解决维修或退租争议']
    },
    {
      caseType: '民间借贷纠纷',
      score: countScore(text, ['借钱', '借款', '欠钱', '还钱', '转账', '借条', '利息', '本金', '催款']),
      subType: (() => {
        const matched = []
        if (includesAny(text, ['没有借条', '没借条', '无借条', '没有欠条'])) matched.push(includesAny(text, ['转账', '流水']) ? '无借条但有转账' : '仅有聊天记录')
        else if (includesAny(text, ['借条', '欠条', '借据'])) matched.push('有借条借款')
        if (includesAny(text, ['利息', '高利', '年利率'])) matched.push('利息争议')
        if (!matched.length && includesAny(text, ['聊天记录', '微信', '短信'])) matched.push('仅有聊天记录')
        return matched.length ? [...new Set(matched)].slice(0, 3).join(' + ') : '民间借贷综合争议'
      })(),
      required: ['loanAmount', 'delivery', 'repaymentDue', 'loanAgreement', 'borrowerIdentity'],
      goals: ['要求返还借款本金', '主张合法利息', '准备起诉/支付令']
    },
    {
      caseType: '消费维权纠纷',
      score: countScore(text, ['退款', '退货', '质量', '虚假宣传', '商家', '平台', '售后', '订单', '发票', '消费者']),
      subType: matchedSubTypes(text, [
        ['虚假宣传', ['虚假宣传', '夸大', '承诺']],
        ['商品质量问题', ['质量', '坏了', '瑕疵', '假货']],
        ['拒绝退款', ['退款', '不退', '拒绝']],
        ['平台售后纠纷', ['平台', '售后', '客服']]
      ], '消费维权综合争议'),
      required: ['order', 'payment', 'defect', 'merchantPromise', 'afterSaleRecord'],
      goals: ['退款/退货/维修', '要求赔偿', '投诉平台或监管部门']
    }
  ]
  const best = candidates.sort((a, b) => b.score - a.score)[0]
  return best.score === 0 ? {
    caseType: '其他民事咨询',
    subType: '待分流',
    required: ['hasWrittenContract', 'hasPaymentRecord', 'hasChatRecord'],
    goals: ['明确法律关系和维权目标']
  } : best
}

function caseTypeCandidates(text) {
  return profilesFor(text)
    .filter((profile) => profile.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 3)
    .map((profile) => `${profile.caseType}(${profile.score})`)
}

function classificationAmbiguous(text) {
  const candidates = profilesFor(text)
    .filter((profile) => profile.score > 0)
    .sort((a, b) => b.score - a.score)
  return candidates.length >= 2 && candidates[0].score >= 2 && candidates[1].score >= 2 && candidates[0].score - candidates[1].score <= 1
}

function profilesFor(text) {
  const labor = {
    caseType: '劳动纠纷',
    score: countScore(text, ['工资', '欠薪', '劳动合同', '辞退', '开除', '工伤', '加班', '社保', '仲裁', '公司', '入职'])
  }
  const lease = {
    caseType: '房屋租赁纠纷',
    score: countScore(text, ['房东', '租房', '租赁', '押金', '租金', '退租', '维修', '中介', '房屋'])
  }
  const loan = {
    caseType: '民间借贷纠纷',
    score: countScore(text, ['借钱', '借款', '欠钱', '还钱', '转账', '借条', '利息', '本金', '催款'])
  }
  const consumer = {
    caseType: '消费维权纠纷',
    score: countScore(text, ['退款', '退货', '质量', '虚假宣传', '商家', '平台', '售后', '订单', '发票', '消费者'])
  }
  return [labor, lease, loan, consumer]
}

function extractFacts(text, profile) {
  const facts = {
    caseType: profile.caseType,
    subType: profile.subType,
    rawDescription: text,
    caseTypeCandidates: caseTypeCandidates(text),
    classificationAmbiguous: classificationAmbiguous(text),
    amounts: findAmounts(text),
    locations: findLocations(text),
    jurisdictionKnown: hasJurisdictionInfo(text),
    hasClaimAmount: hasAmount(text),
    dates: [...new Set([...text.matchAll(/(\d{4}年\d{1,2}月\d{0,2}日?|\d{1,2}月\d{1,2}日|\d+个?月|\d+年)/g)].map((m) => m[1]))],
    contradictions: detectContradictions(text),
    adverseFactors: detectAdverseFactors(profile, text),
    urgentFactors: detectUrgentFactors(profile, text),
    keyDateKnown: hasKeyDate(text),
    partialRepayment: includesAny(text, ['已还', '还了', '部分还款', '还过', '尚欠', '未还余额']),
    interestAgreement: includesAny(text, ['利息', '利率', '年利率', '月息', '逾期利息']),
    withholdingReason: includesAny(text, ['扣款', '扣押金', '损坏', '维修费', '清洁费', '违约金', '扣除']),
    lossAmountKnown: hasAmount(text) || includesAny(text, ['退款', '退货', '赔偿', '损失']),
    hasWrittenContract: includesAny(text, ['合同', '协议', '借条', '欠条']) && !includesAny(text, ['未签', '没签', '没有签', '无合同', '没有合同', '没有借条', '没借条', '无借条', '没有欠条']),
    hasChatRecord: includesAny(text, ['聊天', '微信', '短信', '录音', '沟通']),
    hasPaymentRecord: includesAny(text, ['转账', '流水', '付款', '收据', '发票', '工资条']),
    hasPhotoOrVideo: includesAny(text, ['照片', '视频', '截图'])
  }
  if (profile.caseType === '劳动纠纷') {
    Object.assign(facts, {
      employmentStart: includesAny(text, ['入职', '工作', '上班']) || hasDate(text),
      employer: includesAny(text, ['公司', '单位', '老板', '用人单位']),
      salary: includesAny(text, ['工资', '薪资', '月薪']) || hasAmount(text),
      unpaidPeriod: includesAny(text, ['拖欠', '未发', '欠薪']) && hasDate(text),
      laborEvidence: includesAny(text, ['社保', '考勤', '打卡', '工资流水', '工作群', '工牌', '录用通知']) || (includesAny(text, ['劳动合同', '合同']) && !includesAny(text, ['未签', '没签', '没有签', '无合同'])),
      termination: includesAny(text, ['辞退', '开除', '裁员', '解除'])
    })
  } else if (profile.caseType === '房屋租赁纠纷') {
    Object.assign(facts, {
      leaseContract: includesAny(text, ['租赁合同', '合同', '协议']),
      depositAmount: includesAny(text, ['押金']) || hasAmount(text),
      leaseTerm: includesAny(text, ['租期', '到期', '退租']) || hasDate(text),
      handover: includesAny(text, ['交接', '钥匙', '验房', '照片']),
      communication: includesAny(text, ['微信', '聊天', '通知', '沟通'])
    })
  } else if (profile.caseType === '民间借贷纠纷') {
    Object.assign(facts, {
      loanAmount: hasAmount(text),
      delivery: includesAny(text, ['转账', '现金', '微信转', '支付宝', '银行卡']),
      repaymentDue: includesAny(text, ['还款', '到期', '约定', '期限']) || hasDate(text),
      loanAgreement: includesAny(text, ['聊天记录', '承诺', '微信', '短信']) || (includesAny(text, ['借条', '欠条', '借据']) && !includesAny(text, ['没有借条', '没借条', '无借条', '没有欠条'])),
      borrowerIdentity: includesAny(text, ['身份证', '手机号', '住址', '姓名'])
    })
  } else if (profile.caseType === '消费维权纠纷') {
    Object.assign(facts, {
      order: includesAny(text, ['订单', '下单', '购买']),
      payment: includesAny(text, ['付款', '支付', '发票', '收据']) || hasAmount(text),
      defect: includesAny(text, ['质量', '坏', '瑕疵', '假货', '不能用']),
      merchantPromise: includesAny(text, ['承诺', '宣传', '保证', '广告']),
      afterSaleRecord: includesAny(text, ['售后', '客服', '退款', '退货', '聊天'])
    })
  }
  facts.timelineFocus = timelineFocus(profile)
  facts.jurisdictionFocus = jurisdictionFocus(profile)
  facts.procedureFit = procedureFit(profile, facts)
  facts.claimReadiness = claimReadiness(profile, facts)
  return facts
}

function detectContradictions(text) {
  const contradictions = []
  const deniesLaborContract = includesAny(text, ['未签劳动合同', '没签劳动合同', '没有签劳动合同', '无劳动合同', '没有合同'])
  const affirmsLaborContract = includesAny(text, ['有劳动合同', '签了劳动合同', '签过劳动合同', '劳动合同照片', '合同照片', '合同原件', '合同扫描件'])
  if (deniesLaborContract && affirmsLaborContract) {
    contradictions.push('关于是否签过书面劳动合同的陈述前后不一致，请确认是从未签署、后来补签，还是找到了合同照片/复印件。')
  }
  const deniesLoanNote = includesAny(text, ['没有借条', '没借条', '无借条', '没有欠条', '无欠条'])
  const affirmsLoanNote = includesAny(text, ['有借条', '有欠条', '借条照片', '欠条照片', '借条原件', '欠条原件'])
  if (deniesLoanNote && affirmsLoanNote) {
    contradictions.push('关于是否存在借条/欠条的陈述前后不一致，请确认是否有原件、照片或仅有聊天承诺。')
  }
  const saysRepaid = includesAny(text, ['已经还清', '已还清', '还完了', '全部还了'])
  const saysUnpaid = includesAny(text, ['不还', '没还', '未还', '欠钱', '欠款'])
  if (saysRepaid && saysUnpaid) {
    contradictions.push('关于款项是否已经还清存在冲突，请列明借款本金、已还金额、未还余额和对应凭证。')
  }
  return contradictions
}

function strength(known, total) {
  const ratio = total ? known / total : 0
  if (ratio >= 0.75) return 'strong'
  if (ratio >= 0.45) return 'medium'
  return 'weak'
}

function makeIssue(title, requiredFacts, keys, facts, legalRule, applicationBase) {
  const knownFacts = []
  const missingFacts = []
  keys.forEach((key, index) => {
    const label = requiredFacts[Math.min(index, requiredFacts.length - 1)]
    if (facts[key]) knownFacts.push(label)
    else missingFacts.push(label)
  })
  const evidenceStrength = strength(knownFacts.length, keys.length)
  const evidenceGaps = evidenceGapsFor(title, missingFacts, evidenceStrength)
  const conclusion = evidenceStrength === 'strong'
    ? '初步看该争点具备较好的事实基础，但仍需以原始证据和正式审查为准。'
    : evidenceStrength === 'medium'
      ? '该争点存在可主张方向，但证据链仍需补强。'
      : '该争点目前事实不足，建议先补充关键材料后再判断。'
  return { issue: title, requiredFacts, knownFacts, missingFacts, evidenceGaps, evidenceStrength, legalRule, application: applicationBase, conclusion }
}

function evidenceGapsFor(issueTitle, missingFacts, strengthValue) {
  return missingFacts.map((fact, index) => ({
    fact,
    priority: strengthValue === 'weak' || index === 0 ? 'high' : 'medium',
    suggestion: evidenceSuggestion(issueTitle, fact)
  }))
}

function evidenceSuggestion(issueTitle, fact) {
  const joined = `${issueTitle} ${fact}`
  if (includesAny(joined, ['劳动关系', '用人单位', '入职', '工作事实'])) return '优先补劳动合同、录用通知、考勤、工作群、工牌、社保或工资流水。'
  if (includesAny(joined, ['工资', '欠薪', '已发', '未发'])) return '补工资标准约定、工资流水、工资条、欠薪月份表和催要记录。'
  if (includesAny(joined, ['解除', '辞退'])) return '补解除通知、聊天记录、录音、离职交接材料和公司说明。'
  if (includesAny(joined, ['押金', '租赁', '租期', '交接'])) return '补租赁合同、押金转账、退租交接照片/视频、钥匙交接和扣款明细。'
  if (includesAny(joined, ['维修', '损坏', '扣款'])) return '补损坏前后照片、维修报价/发票、房东扣款说明和沟通记录。'
  if (includesAny(joined, ['借款', '本金', '交付', '还款', '借贷'])) return '补转账流水、聊天承诺、借条/欠条、催款记录、已还款明细和对方身份信息。'
  if (includesAny(joined, ['利息', '未还余额'])) return '补利息约定、还款明细、未还余额计算表和催款确认记录。'
  if (includesAny(joined, ['订单', '商品', '服务', '宣传', '售后', '退款'])) return '补订单、支付凭证、宣传截图、问题照片/视频、检测记录和售后沟通。'
  if (includesAny(joined, ['抗辩', '反证'])) return '围绕对方说法补直接反证，例如原始聊天、付款备注、第三方记录或现场照片。'
  if (includesAny(joined, ['时间', '期限', '时效'])) return '补关键日期的原始凭证，例如合同、转账时间、催告记录、平台工单或聊天截图。'
  return '补能直接证明该事实的原始材料，优先使用带时间、主体和金额信息的证据。'
}

function appendAdverseIssue(issues, facts) {
  if (!facts.adverseFactors?.length) return issues
  issues.push(makeIssue('对方抗辩及反证压力', ['对方抗辩内容', '我方直接反证', '第三方或原始证据'], ['adverseFactors', 'hasChatRecord', 'hasPaymentRecord'], facts, '责任判断不仅看己方陈述，也要评估对方可能提出的付款、赠与、质量、违约、损坏等抗辩。', '已出现对方抗辩线索时，应优先准备能直接回应该抗辩的原始证据，否则结论只能保持为初步可能。'))
  return issues
}

function issuesFor(profile, facts) {
  if (profile.caseType === '劳动纠纷') {
    const issues = [
      makeIssue('是否能够证明劳动关系', ['用人单位主体', '入职/工作事实', '工资或管理关系', '劳动关系证据'], ['employer', 'employmentStart', 'salary', 'laborEvidence'], facts, '劳动关系需结合用工管理、劳动报酬、工作内容等事实综合判断。', '若能提供工资流水、考勤、工作群、社保或录用材料，劳动关系证明力会明显增强。')
    ]
    if (facts.salary) issues.push(makeIssue('拖欠工资责任', ['工资标准', '拖欠期间', '支付记录', '劳动关系'], ['salary', 'unpaidPeriod', 'hasPaymentRecord', 'laborEvidence'], facts, '用人单位应及时足额支付劳动报酬。', '当前已有欠薪方向线索，关键在于固定工资标准、欠薪月份和支付记录。'))
    if (facts.salary) issues.push(makeIssue('欠薪金额和请求范围', ['工资标准', '拖欠月份', '已发/未发记录', '请求金额'], ['salary', 'unpaidPeriod', 'hasPaymentRecord', 'hasClaimAmount'], facts, '工资请求需要把工资标准、拖欠期间、已支付金额和最终请求金额对应起来。', '如果只说拖欠工资但没有列清月份和金额，适合先核算明细，再决定投诉或仲裁请求。'))
    if (!facts.hasWrittenContract) issues.push(makeIssue('未签书面劳动合同责任', ['入职时间', '未签书面合同', '实际用工持续时间', '工资标准'], ['employmentStart', 'hasWrittenContract', 'salary'], facts, '建立劳动关系后应及时订立书面劳动合同。', '用户称未签合同时，应补充入职时间、工作期间和工资标准。'))
    issues.push(makeIssue('仲裁时效和程序前置风险', ['入职/离职或欠薪时间', '最近催告或争议发生时间'], ['employmentStart', 'keyDateKnown'], facts, '劳动争议通常需要先申请劳动仲裁，并结合争议发生、离职、催告等时间判断时效风险。', '时间线越明确，越能判断应先投诉、仲裁还是补强催告记录。'))
    return appendAdverseIssue(issues, facts)
  }
  if (profile.caseType === '房屋租赁纠纷') {
    return appendAdverseIssue([
      makeIssue('押金/租金是否应返还', ['租赁合同', '押金金额', '退租时间', '房屋交接状态'], ['leaseContract', 'depositAmount', 'leaseTerm', 'handover'], facts, '押金返还通常取决于合同约定、违约事实和交接证据。', '如果没有交接照片或扣款明细，押金争议的事实基础会偏弱。'),
      makeIssue('押金扣款和返还金额口径', ['押金金额', '扣款理由', '扣款明细/维修凭证', '交接证据'], ['depositAmount', 'withholdingReason', 'hasPaymentRecord', 'handover'], facts, '押金争议不能只看是否退还，还要核对合同约定、扣款理由、实际损失和剩余应返金额。', '若房东没有提供扣款明细或损失凭证，返还请求方向会更清晰；若确有损坏，则需核算合理扣款。'),
      makeIssue('维修责任或违约责任归属', ['合同约定', '沟通通知', '房屋问题证据'], ['leaseContract', 'communication', 'hasPhotoOrVideo'], facts, '维修责任需结合自然损耗、使用情况及合同约定判断。', '建议补齐照片、维修沟通和费用凭证后再判断责任比例。'),
      makeIssue('租期、退租和起诉节点', ['租期/退租时间', '交接或扣款时间', '通知送达记录'], ['leaseTerm', 'keyDateKnown', 'communication'], facts, '租赁争议的责任边界往往取决于合同期间、提前退租通知、交接和扣款发生时间。', '若能明确退租、交接和扣款日期，押金返还及违约责任判断会更稳定。')
    ], facts)
  }
  if (profile.caseType === '民间借贷纠纷') {
    return appendAdverseIssue([
      makeIssue('借贷合意是否成立', ['借款意思表示', '借款金额', '交付凭证', '还款承诺'], ['loanAgreement', 'loanAmount', 'delivery', 'repaymentDue'], facts, '民间借贷通常需要证明双方存在借贷合意及款项已经交付。', '无借条并不必然不能主张，但需要用转账、聊天记录、催款记录形成证据链。'),
      makeIssue('本金和利息能否支持', ['本金金额', '利息约定', '还款期限', '已还金额'], ['loanAmount', 'interestAgreement', 'repaymentDue', 'partialRepayment'], facts, '本金以实际交付为核心，利息需看约定及是否超过法定保护范围。', '当前应先明确本金、已还金额和利息约定，避免请求金额不清。'),
      makeIssue('未还余额和请求金额口径', ['借款本金', '已还金额', '未还余额', '利息/逾期利息口径'], ['loanAmount', 'partialRepayment', 'hasClaimAmount', 'interestAgreement'], facts, '起诉或支付令请求应区分本金、已还金额、未还余额、利息起算点和计算标准。', '若只有借款总额但没有已还金额或利息约定，初步可判断借贷方向，但请求金额仍不稳定。'),
      makeIssue('诉讼时效和催款节点', ['约定还款日', '最近催款/承认欠款时间', '对方身份送达信息'], ['repaymentDue', 'keyDateKnown', 'borrowerIdentity'], facts, '民间借贷需要关注约定还款日、催收中断/重新确认以及诉讼送达条件。', '若还款日和最近催款时间不清，不能稳定判断是否存在时效抗辩风险。')
    ], facts)
  }
  if (profile.caseType === '消费维权纠纷') {
    return appendAdverseIssue([
      makeIssue('商家是否构成违约或侵权', ['订单', '付款', '商品/服务问题', '宣传承诺', '售后记录'], ['order', 'payment', 'defect', 'merchantPromise', 'afterSaleRecord'], facts, '消费维权围绕交易关系、商品或服务瑕疵、宣传承诺和损失结果展开。', '订单、问题照片、宣传截图和售后记录越完整，退款或赔偿主张越清晰。'),
      makeIssue('退款/赔偿金额和损失范围', ['支付金额', '退款或赔偿目标', '损失证明', '售后处理结果'], ['payment', 'lossAmountKnown', 'defect', 'afterSaleRecord'], facts, '消费维权需把支付金额、退款范围、质量问题或宣传差异造成的损失对应起来。', '若只说想维权但没有订单金额和损失范围，适合先明确退款、维修、更换或赔偿哪一项。'),
      makeIssue('退换货、售后和投诉期限', ['购买/收货时间', '发现问题时间', '首次售后或投诉时间'], ['order', 'keyDateKnown', 'afterSaleRecord'], facts, '消费维权处理效果通常受购买、收货、发现问题和首次售后时间影响。', '如果能明确时间节点，更容易选择平台售后、12315 投诉或诉讼路径。')
    ], facts)
  }
  return [makeIssue('法律关系和请求基础是否明确', ['合同或关系证明', '付款记录', '沟通记录'], ['hasWrittenContract', 'hasPaymentRecord', 'hasChatRecord'], facts, '民事责任判断需要先明确法律关系、请求权基础和证据链。', '当前信息不足以进行稳定责任判断，应先补充事实和证据。')]
}

function evidenceFor(profile, facts) {
  const rules = {
    '劳动纠纷': [
      ['劳动合同/录用通知', '证明劳动关系和岗位、薪资约定', 'strong', ['合同', '录用', 'offer']],
      ['工资流水/转账记录', '证明工资标准和欠薪金额', 'strong', ['工资流水', '银行流水', '转账', '工资条']],
      ['考勤/排班/工作记录', '证明实际提供劳动', 'medium', ['考勤', '打卡', '排班', '工作群']],
      ['社保/个税记录', '辅助证明用工关系', 'medium', ['社保', '个税']]
    ],
    '房屋租赁纠纷': [
      ['租赁合同', '证明租期、押金、违约条款', 'strong', ['合同', '租期']],
      ['押金/租金支付凭证', '证明已付款项和金额', 'strong', ['押金', '租金', '转账', '收据']],
      ['房屋交接清单/照片', '证明退租时房屋状态', 'strong', ['交接', '照片', '视频']]
    ],
    '民间借贷纠纷': [
      ['借条/欠条/还款承诺', '证明借贷合意和还款义务', 'strong', ['借条', '欠条', '承诺']],
      ['转账/取现/收款记录', '证明款项交付', 'strong', ['转账', '流水', '收款']],
      ['聊天和催款记录', '证明借款用途、还款承诺、催告过程', 'medium', ['聊天', '微信', '催款']]
    ],
    '消费维权纠纷': [
      ['订单和支付凭证', '证明交易关系和金额', 'strong', ['订单', '付款', '发票']],
      ['商品问题照片/检测记录', '证明质量或服务瑕疵', 'strong', ['照片', '检测', '质量', '坏']],
      ['宣传页面截图', '证明宣传内容和承诺', 'medium', ['宣传', '截图', '承诺']]
    ]
  }[profile.caseType] ?? [['合同或交易凭证', '证明基础法律关系', 'strong', ['合同', '协议', '订单']], ['沟通记录', '证明协商过程和对方承诺', 'medium', ['聊天', '微信', '短信']]]
  return rules.map(([name, purpose, itemStrength, words]) => {
    let provided = includesAny(facts.rawDescription, words)
    if (name.includes('劳动合同') && includesAny(facts.rawDescription, ['未签', '没签', '没有签', '无合同', '没有合同'])) provided = false
    if ((name.includes('借条') || name.includes('欠条')) && includesAny(facts.rawDescription, ['没有借条', '没借条', '无借条', '没有欠条'])) provided = false
    return { name, purpose, strength: itemStrength, provided }
  })
}

function questionFor(key) {
  return {
    employmentStart: '请补充入职时间、工作岗位、工作地点，以及是否仍在职。',
    employer: '请补充用人单位名称、与你对接的负责人，以及工资由谁支付。',
    salary: '请补充工资标准、发薪周期、拖欠金额和拖欠月份。',
    unpaidPeriod: '请补充从哪几个月开始拖欠工资，是否有工资条或银行流水。',
    laborEvidence: '请说明是否有劳动合同、考勤、社保、工作群、工牌或工资流水。',
    leaseContract: '请补充租赁合同中关于押金、租期、违约和维修责任的约定。',
    depositAmount: '请补充押金/租金金额、支付方式和付款凭证。',
    leaseTerm: '请补充租期、退租时间、是否提前退租以及提前退租原因。',
    handover: '请补充退租交接记录、房屋照片/视频和房东扣款明细。',
    communication: '请补充你与房东/中介关于退租、维修或扣款的聊天记录。',
    loanAmount: '请补充借款金额、实际交付时间和已还金额。',
    delivery: '请补充款项是现金还是转账交付，是否有银行/微信/支付宝流水。',
    repaymentDue: '请补充约定还款时间、催款时间以及对方是否承认欠款。',
    loanAgreement: '请说明是否有借条、欠条、聊天记录、语音或还款承诺。',
    borrowerIdentity: '请补充对方姓名、手机号、住址或身份证等可用于起诉的信息。',
    order: '请补充订单号、购买时间、平台或商家名称。',
    payment: '请补充支付金额、支付凭证、发票或收据。',
    defect: '请描述商品/服务具体问题，并说明是否有照片、视频或检测记录。',
    merchantPromise: '请补充商家宣传页面、承诺内容或销售话术截图。',
    afterSaleRecord: '请补充售后沟通、退款申请、平台处理记录。'
  }[key] ?? `请补充 ${key} 相关事实。`
}

function actionPathFor(profile, score, facts) {
  const steps = []
  if (score < 0.6) steps.push('先按追问清单补充事实，避免在关键信息不足时直接判断责任。', '立即保存原始证据，不要只保存转述内容。')
  if (profile.caseType === '劳动纠纷') steps.push('整理劳动关系证据、工资标准和欠薪明细。', facts.procedureFit, '注意劳动仲裁时效，尽量保留书面催告和沟通记录。')
  else if (profile.caseType === '民间借贷纠纷') steps.push('整理借贷合意、款项交付、催款和对方身份信息。', '发送书面催款通知，固定对方是否承认欠款。', facts.procedureFit)
  else if (profile.caseType === '房屋租赁纠纷') steps.push('整理合同、付款凭证、交接照片和扣款明细。', '先书面通知房东/中介说明请求，保留送达记录。', facts.procedureFit)
  else if (profile.caseType === '消费维权纠纷') steps.push('整理订单、支付、问题证据、宣传页面和售后记录。', facts.procedureFit)
  else steps.push('先明确双方身份、法律关系、请求目标和证据基础。')
  return steps
}

function amountQuestions(profile, facts) {
  const questions = []
  if (profile.caseType === '劳动纠纷' && facts.salary && !facts.hasClaimAmount) {
    questions.push('请列明每月工资标准、拖欠月份、已发金额和你准备主张的欠薪总额。')
  } else if (profile.caseType === '房屋租赁纠纷' && facts.depositAmount && !facts.withholdingReason) {
    questions.push('请补充房东拒退或扣押金的具体理由、扣款明细，以及是否有维修/清洁/损坏凭证。')
  } else if (profile.caseType === '民间借贷纠纷' && facts.loanAmount && !facts.partialRepayment) {
    questions.push('请补充对方已还金额、未还余额、是否约定利息，以及你准备主张的本金和利息计算方式。')
  } else if (profile.caseType === '消费维权纠纷' && facts.payment && !facts.lossAmountKnown) {
    questions.push('请补充订单支付金额、希望退款/维修/更换/赔偿的具体请求，以及损失如何计算。')
  } else if (!facts.hasClaimAmount) {
    questions.push('请补充你准备主张的金额、计算依据和已付款/已还款情况。')
  }
  return questions
}

function amountRisks(profile, facts) {
  const risks = []
  if (profile.caseType === '劳动纠纷' && facts.salary && !facts.hasClaimAmount) risks.push('欠薪请求金额尚未核算清楚，仲裁请求过高或过低都可能影响维权效率。')
  if (profile.caseType === '房屋租赁纠纷' && facts.depositAmount && !facts.withholdingReason) risks.push('押金争议缺少扣款依据和金额明细，暂不宜直接认定房东需全额返还。')
  if (profile.caseType === '民间借贷纠纷' && facts.loanAmount && !facts.partialRepayment) risks.push('未区分借款本金、已还金额和未还余额，起诉请求金额可能需要进一步核算。')
  if (profile.caseType === '消费维权纠纷' && facts.payment && !facts.lossAmountKnown) risks.push('退款或赔偿范围尚不明确，建议先固定订单金额、问题证据和售后处理结果。')
  return risks
}

function claimReadiness(profile, facts) {
  const items = []
  const hasAdverse = Boolean(facts.adverseFactors?.length)
  const hasUrgent = Boolean(facts.urgentFactors?.length)
  const item = (claim, status, reason, nextEvidence) => ({ claim, status, reason, nextEvidence })
  if (profile.caseType === '劳动纠纷') {
    items.push(item('支付拖欠工资', facts.salary && facts.unpaidPeriod && facts.hasPaymentRecord ? 'ready' : 'needs_evidence', '工资请求以工资标准、拖欠月份、支付记录和劳动关系为核心。', '补工资流水、工资条、欠薪月份表和劳动关系证据。'))
    if (!facts.hasWrittenContract) items.push(item('未签书面劳动合同责任', facts.employmentStart && facts.salary && facts.laborEvidence ? 'ready' : 'needs_evidence', '二倍工资等请求需要证明入职时间、实际用工和未签书面合同状态。', '补入职材料、考勤、工作群、工资流水和是否补签合同的说明。'))
    if (facts.termination) items.push(item('解除/辞退赔偿', hasAdverse ? 'high_risk' : 'needs_evidence', '解除类请求取决于解除原因、通知程序、工作年限和工资标准。', '补辞退通知、解除理由、聊天/录音、规章制度依据和工作年限。'))
  } else if (profile.caseType === '房屋租赁纠纷') {
    items.push(item('返还押金/租金', hasAdverse || facts.withholdingReason ? 'high_risk' : facts.leaseContract && facts.depositAmount && facts.handover ? 'ready' : 'needs_evidence', '押金返还取决于合同约定、押金金额、交接状态和扣款依据。', '补租赁合同、押金凭证、交接照片/清单、扣款明细和沟通记录。'))
    items.push(item('维修/违约责任', facts.leaseContract && facts.communication && facts.hasPhotoOrVideo ? 'ready' : 'needs_evidence', '维修或违约责任需要证明损坏原因、通知记录、合同约定和损失金额。', '补房屋问题照片、维修报价/发票、通知记录和合同条款。'))
  } else if (profile.caseType === '民间借贷纠纷') {
    items.push(item('返还借款本金', hasAdverse || hasUrgent ? 'high_risk' : facts.loanAmount && facts.delivery && facts.loanAgreement && facts.repaymentDue && facts.borrowerIdentity ? 'ready' : 'needs_evidence', '本金请求需要证明借贷合意、款项交付、到期未还和对方身份。', '补转账流水、聊天承诺、催款记录、已还款明细和身份/地址信息。'))
    items.push(item('主张利息或逾期利息', facts.interestAgreement && facts.repaymentDue ? 'ready' : 'needs_evidence', '利息请求需要有约定、起算时间和合法范围。', '补利息约定、还款期限、逾期起算点和计算表。'))
  } else if (profile.caseType === '消费维权纠纷') {
    items.push(item('退款/退货/维修', facts.order && facts.payment && facts.afterSaleRecord && (facts.defect || facts.merchantPromise) ? 'ready' : 'needs_evidence', '退款或维修需要证明交易关系、问题事实、商家承诺和售后记录。', '补订单、支付凭证、问题照片/检测、宣传截图和售后沟通。'))
    items.push(item('赔偿损失', facts.lossAmountKnown && facts.defect ? 'ready' : 'needs_evidence', '赔偿请求需要说明损失范围、因果关系和金额计算。', '补损失清单、维修/检测费用、退款差额和平台处理结果。'))
  } else {
    items.push(item('明确可主张请求', 'needs_evidence', '当前法律关系未完全明确。', '先补双方身份、合同/交易凭证、金额、时间线和沟通记录。'))
  }
  return items
}

function legalBasisCitations(profile, facts) {
  const flk = 'https://flk.npc.gov.cn/'
  const court = 'https://www.court.gov.cn/'
  const citations = []
  if (profile.caseType === '劳动纠纷') {
    citations.push({ type: 'article', title: '《中华人民共和国劳动合同法》：劳动合同订立、工资支付和解除相关规则', sourceUrl: flk })
    citations.push({ type: 'article', title: '《中华人民共和国劳动争议调解仲裁法》：劳动仲裁程序和时效相关规则', sourceUrl: flk })
  } else if (profile.caseType === '房屋租赁纠纷') {
    citations.push({ type: 'article', title: '《中华人民共和国民法典》：租赁合同、违约责任和押金返还相关规则', sourceUrl: flk })
  } else if (profile.caseType === '民间借贷纠纷') {
    citations.push({ type: 'article', title: '《中华人民共和国民法典》：借款合同、合同履行和违约责任相关规则', sourceUrl: flk })
    citations.push({ type: 'article', title: '最高人民法院关于审理民间借贷案件适用法律若干问题的规定', sourceUrl: court })
  } else if (profile.caseType === '消费维权纠纷') {
    citations.push({ type: 'article', title: '《中华人民共和国消费者权益保护法》：消费者退款、赔偿和经营者责任相关规则', sourceUrl: flk })
    citations.push({ type: 'article', title: '《中华人民共和国产品质量法》：产品质量责任相关规则', sourceUrl: flk })
  } else {
    citations.push({ type: 'article', title: '《中华人民共和国民法典》：民事主体、合同和侵权责任的一般规则', sourceUrl: flk })
  }
  if (facts.classificationAmbiguous) citations.push({ type: 'guidance', title: '案由交叉时需先确认基础法律关系，再分别检索对应规则', sourceUrl: flk })
  if (facts.adverseFactors.length) citations.push({ type: 'guidance', title: '存在对方抗辩时，应围绕争议事实补充反证后再作结论', sourceUrl: flk })
  return citations.filter((item, index, arr) => arr.findIndex((other) => other.type === item.type && other.title === item.title && other.sourceUrl === item.sourceUrl) === index)
}

function analyze(text) {
  const masked = text.replace(/(\d{6})\d{8}(\d{4})/g, '$1********$2').replace(/(1[3-9]\d)\d{4}(\d{4})/g, '$1****$2')
  const profile = profileFor(masked)
  const facts = extractFacts(masked, profile)
  const missingKeys = profile.required.filter((key) => !facts[key])
  const completenessScore = Math.round(((profile.required.length - missingKeys.length) / profile.required.length) * 100) / 100
  const missingQuestions = [
    ...facts.contradictions.map((item) => `请先澄清：${item}`),
    ...(facts.classificationAmbiguous ? ['当前描述可能同时涉及多个法律关系，请先确认主诉求和对方身份：是劳动用工、消费退款、借贷返还、租赁押金，还是多个请求需要分别处理？'] : []),
    ...facts.adverseFactors.map((item) => `请补充能回应对方抗辩的证据：${item}`),
    ...facts.urgentFactors.map((item) => `请优先确认紧急情况：${item}`),
    ...(facts.keyDateKnown ? [] : [facts.timelineFocus]),
    ...(facts.jurisdictionKnown ? [] : [facts.jurisdictionFocus]),
    ...amountQuestions(profile, facts),
    ...missingKeys.map(questionFor)
  ].filter((item, index, arr) => arr.indexOf(item) === index).slice(0, 8)
  const issues = issuesFor(profile, facts)
  const evidenceAssessments = evidenceFor(profile, facts)
  const evidenceList = evidenceAssessments.map((item) => `${item.name}：${item.purpose}${item.provided ? '（已出现线索）' : '（建议补充）'}`)
  const liabilityAnalysis = issues.map((item) => `【${item.issue}】${item.application} ${item.conclusion}`)
  const actionPath = actionPathFor(profile, completenessScore, facts)
  if (!facts.keyDateKnown) actionPath.unshift('先补一张时间线：发生时间、通知/催告时间、对方回应时间和目前状态。')
  if (!facts.jurisdictionKnown) actionPath.unshift('补充管辖地点信息，确认应向哪个劳动仲裁委、法院、平台或监管部门提交材料。')
  ;[...facts.urgentFactors].reverse().forEach((item) => actionPath.unshift(`紧急处理：${item}`))
  const riskWarnings = ['仅供初步参考，不构成正式法律意见；不要把本分析直接等同于法院或仲裁结果。', '不要伪造、篡改、隐瞒证据，也不要诱导对方作虚假陈述。']
  facts.contradictions.forEach((item) => riskWarnings.push(`存在事实冲突：${item} 在澄清前不宜据此作强结论。`))
  if (facts.classificationAmbiguous) riskWarnings.push(`案由识别存在交叉：${facts.caseTypeCandidates.join('、')}。若法律关系分流错误，后续责任和路径判断会偏差。`)
  facts.adverseFactors.forEach((item) => riskWarnings.push(`对方可能抗辩：${item} 若缺少直接反证，分析结论应下调为审慎判断。`))
  facts.urgentFactors.forEach((item) => riskWarnings.push(`紧急风险：${item} 建议优先固定证据并尽快选择正式程序。`))
  if (completenessScore < 0.6) riskWarnings.push('当前事实完整度偏低，直接判断责任可能误导，应优先补充关键事实。')
  if (!facts.keyDateKnown) riskWarnings.push(facts.timelineFocus)
  if (!facts.jurisdictionKnown) riskWarnings.push(facts.jurisdictionFocus)
  riskWarnings.push(...amountRisks(profile, facts))
  const conclusionLevel = completenessScore < 0.45 ? 'needs_more_facts' : facts.classificationAmbiguous ? 'preliminary_possible' : facts.urgentFactors.length ? 'preliminary_possible' : facts.adverseFactors.length ? 'preliminary_possible' : completenessScore >= 0.75 && issues.some((item) => item.evidenceStrength === 'strong') ? 'preliminary_supported' : 'preliminary_possible'
  const citations = legalBasisCitations(profile, facts)

  let reply = '仅供初步参考，不构成正式法律意见；复杂或高风险案件建议咨询专业律师。\n\n'
  reply += `案件识别：${profile.caseType} / ${profile.subType}\n`
  reply += `诉求目标：${profile.goals.join('、')}\n`
  reply += `事实完整度：${Math.round(completenessScore * 100)}%（${conclusionLevel}）\n\n`
  if (missingQuestions.length) reply += `优先追问：\n${missingQuestions.map((q) => `- ${q}`).join('\n')}\n\n`
  reply += `争点分析：\n${issues.map((issue) => `- 【${issue.issue}】${issue.application} ${issue.conclusion} 已知：${issue.knownFacts.join('、') || '暂无'}；缺失：${issue.missingFacts.join('、') || '暂无'}。`).join('\n')}\n\n`
  reply += `证据强度：\n${evidenceAssessments.map((item) => `- ${item.name}：${item.purpose}${item.provided ? '（已出现线索）' : '（建议补充）'}`).join('\n')}\n\n`
  reply += `建议路径：\n${actionPath.map((x) => `- ${x}`).join('\n')}\n\n`
  reply += `风险提示：\n${riskWarnings.map((x) => `- ${x}`).join('\n')}`

  return {
    id: nextId++,
    caseType: profile.caseType,
    subType: profile.subType,
    claimGoals: profile.goals,
    completenessScore,
    conclusionLevel,
    factsJson: JSON.stringify(facts),
    missingQuestionsJson: JSON.stringify(missingQuestions),
    issuesJson: JSON.stringify(issues),
    evidenceAssessmentsJson: JSON.stringify(evidenceAssessments),
    evidenceJson: JSON.stringify(evidenceList),
    liabilityJson: JSON.stringify(liabilityAnalysis),
    actionPathJson: JSON.stringify(actionPath),
    risksJson: JSON.stringify(riskWarnings),
    citationsJson: JSON.stringify(citations),
    updatedAt: new Date().toISOString(),
    reply
  }
}

function reportMarkdown(analysisData) {
  const facts = JSON.parse(analysisData.factsJson || '{}')
  return `# 法律责任初步分析报告

仅供初步参考，不构成正式法律意见；复杂或高风险案件建议咨询专业律师。

## 案件类型
${analysisData.caseType} / ${analysisData.subType || '待确认'}

## 事实完整度
${Math.round((analysisData.completenessScore || 0) * 100)}%（${analysisData.conclusionLevel}）

## 请求项成熟度
\`\`\`json
${JSON.stringify(facts.claimReadiness || [], null, 2)}
\`\`\`

## 缺失信息
\`\`\`json
${analysisData.missingQuestionsJson}
\`\`\`

## 争点分析与证据缺口
\`\`\`json
${analysisData.issuesJson}
\`\`\`

## 证据清单
\`\`\`json
${analysisData.evidenceJson}
\`\`\`

## 维权路径
\`\`\`json
${analysisData.actionPathJson}
\`\`\`

## 风险提示
\`\`\`json
${analysisData.risksJson}
\`\`\`

## 参考依据
\`\`\`json
${analysisData.citationsJson}
\`\`\`
`
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost:8080')
  if (req.method === 'OPTIONS') return json(res, null)

  if (url.pathname === '/api/health') {
    return json(res, {
      service: 'law-agent-demo',
      version: DEMO_VERSION,
      engine: LLM_ENABLED ? 'rule-agent + llm-multi-agent-review' : 'rule-agent',
      llmProvider: LLM_PROVIDER,
      llmModel: LLM_ENABLED ? LLM_MODEL : '',
      llmConfigured: LLM_ENABLED,
      note: LLM_ENABLED ? 'Demo uses rule draft plus LLM multi-agent review.' : 'Demo uses rule Agent only. Set LLM_PROVIDER, LLM_API_KEY and optional LLM_MODEL to enable LLM review.'
    })
  }

  if (url.pathname === '/api/auth/register' || url.pathname === '/api/auth/login') {
    const body = await readBody(req)
    return json(res, { token: 'demo-token', userId: 1, username: body.username || 'demo', roleCode: 'USER' })
  }

  if (url.pathname === '/api/legal-sessions' && req.method === 'POST') {
    const body = await readBody(req)
    const session = { id: nextId++, userId: 1, title: body.title || '新的法律咨询', status: 'ACTIVE', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
    sessions.unshift(session)
    messages.set(session.id, [])
    return json(res, session)
  }

  if (url.pathname === '/api/legal-sessions' && req.method === 'GET') return json(res, sessions)

  const sessionDetail = url.pathname.match(/^\/api\/legal-sessions\/(\d+)$/)
  if (sessionDetail && req.method === 'GET') {
    const id = Number(sessionDetail[1])
    const session = sessions.find((item) => item.id === id)
    if (!session) return fail(res, '会话不存在', 404)
    return json(res, { session, messages: messages.get(id) || [] })
  }

  if (sessionDetail && req.method === 'PATCH') {
    const body = await readBody(req)
    const session = sessions.find((item) => item.id === Number(sessionDetail[1]))
    if (session) session.title = body.title || session.title
    return json(res, session)
  }

  if (sessionDetail && req.method === 'DELETE') {
    const id = Number(sessionDetail[1])
    const index = sessions.findIndex((item) => item.id === id)
    if (index >= 0) sessions.splice(index, 1)
    return json(res, null)
  }

  const stream = url.pathname.match(/^\/api\/chat\/stream\/(\d+)$/)
  if (stream) {
    const sessionId = Number(stream[1])
    const content = url.searchParams.get('content') || ''
    const list = messages.get(sessionId) || []
    list.push({ id: nextId++, sessionId, userId: 1, role: 'USER', content, metadata: '{}', createdAt: new Date().toISOString() })
    const mergedFacts = list
      .filter((item) => item.role === 'USER')
      .map((item, index) => `第${index + 1}次用户陈述：${item.content}`)
      .join('\n')
    let result = analyze(mergedFacts)
    analyses.set(sessionId, { ...result, sessionId })
    const session = sessions.find((item) => item.id === sessionId)
    if (session) {
      session.caseType = result.caseType
      session.updatedAt = new Date().toISOString()
    }
    res.writeHead(200, { 'Content-Type': 'text/event-stream; charset=utf-8', 'Access-Control-Allow-Origin': '*' })
    let assistantText = ''
    if (LLM_ENABLED) {
      writeSse(res, 'status', `规则 Agent 已完成初步分析，正在调用 ${LLM_PROVIDER}/${LLM_MODEL} 进行多 Agent 流式复核...`)
      try {
        const prompt = buildStreamingReplyPrompt(mergedFacts, compactAnalysisForLlm(result))
        assistantText = await streamOpenAiCompatibleReply(prompt, (chunk) => {
          writeSse(res, 'message', chunk)
        })
        if (!assistantText.trim()) {
          assistantText = result.reply
          writeSse(res, 'message', assistantText)
        }
        result = { ...result, reply: assistantText }
      } catch (error) {
        assistantText = result.reply
        const risks = safeJsonParse(result.risksJson) || []
        risks.unshift(`LLM 流式增强失败，已回退到规则 Agent：${error.message}`)
        result = { ...result, risksJson: JSON.stringify(risks), reply: `${result.reply}\n\nLLM 流式增强失败，已回退到规则 Agent：${error.message}` }
        writeSse(res, 'message', result.reply)
      }
    } else {
      assistantText = result.reply
      for (const line of result.reply.split('\n')) {
        writeSse(res, 'message', line + '\n')
        await new Promise((resolve) => setTimeout(resolve, 20))
      }
    }
    analyses.set(sessionId, { ...result, sessionId })
    list.push({ id: nextId++, sessionId, userId: 1, role: 'ASSISTANT', content: assistantText || result.reply, metadata: JSON.stringify(result), createdAt: new Date().toISOString() })
    messages.set(sessionId, list)
    writeSse(res, 'done', {})
    return res.end()
  }

  const analysis = url.pathname.match(/^\/api\/case-analyses\/(\d+)$/)
  if (analysis) return json(res, analyses.get(Number(analysis[1])) || null)

  const report = url.pathname.match(/^\/api\/reports\/(\d+)$/)
  if (report && req.method === 'POST') {
    const sessionId = Number(report[1])
    const analysisData = analyses.get(sessionId)
    if (!analysisData) return fail(res, '暂无案件分析结果')
    const saved = { id: nextId++, sessionId, userId: 1, title: '法律责任初步分析报告', contentMd: reportMarkdown(analysisData), createdAt: new Date().toISOString() }
    reports.set(saved.id, saved)
    return json(res, saved)
  }

  return fail(res, '接口不存在', 404)
})

server.listen(PORT, () => console.log(`Demo backend listening on http://localhost:${PORT}`))
