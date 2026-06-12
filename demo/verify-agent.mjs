import { spawn } from 'node:child_process'

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

async function request(path, options = {}) {
  const response = await fetch(`http://localhost:8080${path}`, options)
  const payload = await response.json()
  if (payload.code !== 0) {
    throw new Error(payload.message || `Request failed: ${path}`)
  }
  return payload.data
}

async function hasRunningBackend() {
  try {
    const response = await fetch('http://localhost:8080/api/health')
    const payload = await response.json()
    return payload.code === 0 && payload.data?.service === 'law-agent-demo'
  } catch {
    return false
  }
}

async function createSession(title) {
  return request('/api/legal-sessions', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ title })
  })
}

async function send(sessionId, text) {
  const stream = await fetch(`/api/chat/stream/${sessionId}?content=${encodeURIComponent(text)}`.replace('/api', 'http://localhost:8080/api'))
  await stream.text()
  return request(`/api/case-analyses/${sessionId}`)
}

async function generateReport(sessionId) {
  return request(`/api/reports/${sessionId}`, { method: 'POST' })
}

function parse(raw) {
  return JSON.parse(raw || '[]')
}

async function runCase(name, turns, checks) {
  const session = await createSession(name)
  let analysis = null
  for (const turn of turns) {
    analysis = await send(session.id, turn)
  }
  checks(analysis)
  console.log(`PASS ${name}`)
}

let server = null

try {
  if (!(await hasRunningBackend())) {
    server = spawn(process.execPath, ['demo/backend/server.mjs'], {
      cwd: process.cwd(),
      stdio: ['ignore', 'pipe', 'pipe']
    })
    server.stderr.on('data', (chunk) => process.stderr.write(chunk))
    await wait(1000)
  }

  await runCase('劳动纠纷多轮补充', [
    '公司拖欠工资三个月且未签劳动合同',
    '我是2024年3月入职，月工资8000元，有工资转账记录、考勤打卡和工作群聊天，公司叫杭州某科技有限公司'
  ], (analysis) => {
    const issues = parse(analysis.issuesJson)
    const questions = parse(analysis.missingQuestionsJson)
    const facts = JSON.parse(analysis.factsJson)
    const citations = parse(analysis.citationsJson)
    assert(analysis.caseType === '劳动纠纷', 'labor case type mismatch')
    assert(analysis.subType.includes('未签劳动合同') && analysis.subType.includes('拖欠工资'), 'labor subtype should keep multiple issues')
    assert(Array.isArray(analysis.claimGoals) && analysis.claimGoals.length > 0, 'labor claim goals should be present')
    assert(typeof analysis.completenessScore === 'number' && analysis.completenessScore >= 0.8, 'labor completeness should be high after supplement')
    assert(analysis.conclusionLevel === 'preliminary_supported', 'labor conclusion level should be supported after supplement')
    assert(facts.amounts.includes('8000元'), 'labor amount should include salary with unit')
    assert(!facts.amounts.includes('1') && !facts.amounts.includes('2') && !facts.amounts.includes('2024'), 'turn numbers and dates should not be money amounts')
    assert(facts.claimReadiness.some((item) => item.claim === '支付拖欠工资' && item.status === 'ready'), 'wage claim should be ready with payment and timeline evidence')
    assert(facts.claimReadiness.some((item) => item.claim === '未签书面劳动合同责任' && item.status === 'ready'), 'unsigned contract claim should be ready with labor evidence')
    assert(questions.length === 0, 'labor questions should be cleared after supplement')
    assert(issues.some((item) => item.issue === '是否能够证明劳动关系' && item.evidenceStrength === 'strong'), 'labor relationship should be strong')
    assert(issues.some((item) => item.issue === '拖欠工资责任' && item.evidenceStrength === 'strong'), 'wage issue should be strong')
    assert(citations.some((item) => item.title.includes('劳动合同法')), 'labor citation should include labor contract law')
    assert(citations.some((item) => item.title.includes('劳动争议调解仲裁法')), 'labor citation should include arbitration law')
  })

  {
    const session = await createSession('报告结构化章节')
    await send(session.id, '朋友借钱2万元不还，有微信聊天和银行转账记录，对方姓名手机号都有，2024年8月1日到期')
    const report = await generateReport(session.id)
    assert(report.contentMd.includes('## 请求项成熟度'), 'report should include claim readiness section')
    assert(report.contentMd.includes('## 争点分析与证据缺口'), 'report should include issue evidence gap section')
    assert(report.contentMd.includes('## 参考依据'), 'report should include citation section')
    assert(report.contentMd.includes('返还借款本金'), 'report should include claim readiness content')
    console.log('PASS 报告包含结构化分析章节')
  }

  await runCase('欠薪证据清楚时优先劳动监察路径', [
    '公司拖欠工资8000元，我是2024年3月入职，有工资流水、考勤打卡和工作群聊天，公司叫杭州某科技有限公司'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const actionPath = parse(analysis.actionPathJson)
    assert(facts.procedureFit.includes('劳动监察投诉'), 'clear wage arrears should prefer labor supervision first')
    assert(actionPath.some((item) => item.includes('劳动监察投诉')), 'labor action path should mention supervision complaint')
  })

  await runCase('混合描述触发案由澄清', [
    '公司拖欠工资，平台订单也拒绝退款，我不知道该走劳动还是消费维权'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const questions = parse(analysis.missingQuestionsJson)
    const risks = parse(analysis.risksJson)
    const citations = parse(analysis.citationsJson)
    assert(facts.classificationAmbiguous === true, 'mixed labor/consumer description should be marked ambiguous')
    assert(facts.caseTypeCandidates.some((item) => item.includes('劳动纠纷')) && facts.caseTypeCandidates.some((item) => item.includes('消费维权纠纷')), 'mixed case should expose candidate case types')
    assert(questions.some((item) => item.includes('多个法律关系') && item.includes('主诉求')), 'mixed case should ask to clarify primary claim')
    assert(risks.some((item) => item.includes('案由识别存在交叉')), 'mixed case should warn about case routing risk')
    assert(citations.some((item) => item.title.includes('案由交叉')), 'mixed case should include classification guidance citation')
  })

  await runCase('无借条但有转账借贷', [
    '朋友借钱2万元不还，没有借条但有微信聊天和银行转账记录，对方姓名手机号都有'
  ], (analysis) => {
    const evidence = parse(analysis.evidenceAssessmentsJson)
    const facts = JSON.parse(analysis.factsJson)
    const questions = parse(analysis.missingQuestionsJson)
    const risks = parse(analysis.risksJson)
    const issues = parse(analysis.issuesJson)
    const citations = parse(analysis.citationsJson)
    assert(analysis.caseType === '民间借贷纠纷', 'loan case type mismatch')
    assert(analysis.subType === '无借条但有转账', 'loan subtype should respect no-note negation')
    assert(analysis.conclusionLevel === 'preliminary_supported', 'loan conclusion level should be supported')
    assert(facts.amounts.includes('2万元'), 'loan amount should keep money unit')
    assert(facts.keyDateKnown === false, 'loan without due date should mark timeline as missing')
    assert(questions.some((item) => item.includes('借款交付日期') && item.includes('约定还款日')), 'loan without dates should ask timeline question')
    assert(risks.some((item) => item.includes('最近一次催款时间')), 'loan without dates should warn about limitation timeline')
    assert(issues.some((item) => item.issue === '诉讼时效和催款节点' && item.evidenceStrength === 'weak'), 'loan limitation issue should be weak without dates')
    assert(issues.some((item) => item.issue === '诉讼时效和催款节点' && item.evidenceGaps.some((gap) => gap.priority === 'high' && gap.suggestion.includes('关键日期'))), 'weak limitation issue should provide high-priority date evidence gap')
    assert(citations.some((item) => item.title.includes('民法典')), 'loan citation should include civil code')
    assert(citations.some((item) => item.title.includes('民间借贷')), 'loan citation should include private lending judicial rule')
    assert(evidence.some((item) => item.name.includes('借条') && item.provided === false), 'loan note should not be marked provided')
    assert(evidence.some((item) => item.name.includes('转账') && item.provided === true), 'transfer evidence should be marked provided')
  })

  await runCase('借贷补充还款期限后争点增强', [
    '朋友借钱2万元不还，没有借条但有微信聊天和银行转账记录，对方姓名手机号都有',
    '2024年5月1日转账，约定2024年8月1日还款，2025年1月我微信催款，对方承认还欠2万元'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const issues = parse(analysis.issuesJson)
    const questions = parse(analysis.missingQuestionsJson)
    const actionPath = parse(analysis.actionPathJson)
    assert(facts.keyDateKnown === true, 'loan timeline should be known after supplement')
    assert(facts.procedureFit.includes('支付令'), 'clear loan should consider payment order')
    assert(issues.some((item) => item.issue === '诉讼时效和催款节点' && item.evidenceStrength === 'strong'), 'loan limitation issue should become strong after dates')
    assert(issues.some((item) => item.issue === '诉讼时效和催款节点' && item.evidenceGaps.length === 0), 'strong limitation issue should clear evidence gaps')
    assert(!questions.some((item) => item.includes('借款交付日期') && item.includes('约定还款日')), 'timeline question should clear after supplement')
    assert(actionPath.some((item) => item.includes('支付令')), 'clear loan action path should mention payment order')
  })

  await runCase('借贷请求金额口径随还款事实变化', [
    '朋友借钱2万元不还，有微信聊天和银行转账记录，对方姓名手机号都有，2024年8月1日到期',
    '对方后来还了5000元，尚欠15000元，我们没有约定利息，我只主张未还本金15000元'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const issues = parse(analysis.issuesJson)
    const questions = parse(analysis.missingQuestionsJson)
    const risks = parse(analysis.risksJson)
    assert(facts.partialRepayment === true, 'partial repayment should be detected')
    assert(facts.interestAgreement === true, 'interest/no-interest statement should be captured')
    assert(facts.jurisdictionKnown === false, 'loan without address should mark jurisdiction missing')
    assert(questions.some((item) => item.includes('住所地') && item.includes('支付令管辖')), 'loan without jurisdiction should ask address/court venue')
    assert(issues.some((item) => item.issue === '未还余额和请求金额口径' && item.evidenceStrength === 'strong'), 'claim amount issue should be strong with balance facts')
    assert(!questions.some((item) => item.includes('已还金额') && item.includes('未还余额')), 'balance question should clear after supplement')
    assert(!risks.some((item) => item.includes('未区分借款本金')), 'balance risk should clear after supplement')
  })

  await runCase('借贷补充管辖地点后清除管辖追问', [
    '朋友借钱2万元不还，有微信聊天和银行转账记录，对方姓名手机号都有，2024年8月1日到期',
    '对方经常居住地在杭州市西湖区，借款也是在杭州转账交付'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const questions = parse(analysis.missingQuestionsJson)
    const actionPath = parse(analysis.actionPathJson)
    assert(facts.jurisdictionKnown === true, 'jurisdiction should be known after address supplement')
    assert(facts.locations.some((item) => item.includes('杭州市')), 'locations should include Hangzhou')
    assert(!questions.some((item) => item.includes('支付令管辖')), 'jurisdiction question should clear after supplement')
    assert(!actionPath.some((item) => item.includes('补充管辖地点信息')), 'action path should not ask jurisdiction after supplement')
  })

  await runCase('借贷出现失联和转移财产紧急风险', [
    '朋友借钱20万元不还，有微信聊天和银行转账记录，对方姓名手机号都有，2024年8月1日到期，对方现在失联还说要卖车转移财产'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const questions = parse(analysis.missingQuestionsJson)
    const risks = parse(analysis.risksJson)
    const actionPath = parse(analysis.actionPathJson)
    assert(Array.isArray(facts.urgentFactors) && facts.urgentFactors.length >= 2, 'urgent factors should detect missing contact and asset transfer')
    assert(facts.claimReadiness.some((item) => item.claim === '返还借款本金' && item.status === 'high_risk'), 'urgent loan principal claim should be high risk')
    assert(analysis.conclusionLevel === 'preliminary_possible', 'urgent risk should downgrade to cautious conclusion')
    assert(questions.some((item) => item.includes('紧急情况') && item.includes('失联')), 'urgent missing contact should be asked')
    assert(risks.some((item) => item.includes('紧急风险') && item.includes('财产转移')), 'asset transfer should be in risks')
    assert(actionPath.some((item) => item.startsWith('紧急处理') && item.includes('财产保全')), 'action path should prioritize preservation risk')
  })

  await runCase('借贷出现赠与抗辩后结论降级', [
    '朋友借钱2万元不还，有微信聊天和银行转账记录，对方姓名手机号都有，2024年8月1日到期',
    '对方现在说是赠与不是借款，但聊天里他说过会还钱，我只主张本金2万元，没有约定利息'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const issues = parse(analysis.issuesJson)
    const questions = parse(analysis.missingQuestionsJson)
    const risks = parse(analysis.risksJson)
    const actionPath = parse(analysis.actionPathJson)
    assert(Array.isArray(facts.adverseFactors) && facts.adverseFactors.some((item) => item.includes('否认借贷合意')), 'gift defense should be detected')
    assert(analysis.conclusionLevel === 'preliminary_possible', 'adverse defense should downgrade supported conclusion')
    assert(facts.procedureFit.includes('普通民事诉讼'), 'gift defense should prefer ordinary litigation')
    assert(issues.some((item) => item.issue === '对方抗辩及反证压力'), 'adverse defense issue should be present')
    assert(questions.some((item) => item.includes('回应对方抗辩') && item.includes('赠与')), 'adverse defense should create follow-up question')
    assert(risks.some((item) => item.includes('对方可能抗辩') && item.includes('赠与')), 'adverse defense should be in risks')
    assert(actionPath.some((item) => item.includes('普通民事诉讼') && item.includes('反证材料')), 'gift defense action path should avoid payment order first')
  })

  await runCase('押金返还租赁', [
    '房东不退押金3000元，我有租赁合同、押金转账记录、退租交接照片和微信聊天'
  ], (analysis) => {
    const issues = parse(analysis.issuesJson)
    assert(analysis.caseType === '房屋租赁纠纷', 'lease case type mismatch')
    assert(analysis.subType.includes('押金返还'), 'lease subtype should include deposit return')
    assert(issues.some((item) => item.issue === '押金/租金是否应返还' && item.evidenceStrength === 'strong'), 'deposit issue should be strong')
  })

  await runCase('押金扣款抗辩影响返还判断', [
    '房东不退押金3000元，我有租赁合同，2025年1月退租，房东说损坏家具要扣维修费'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const issues = parse(analysis.issuesJson)
    const risks = parse(analysis.risksJson)
    assert(facts.withholdingReason === true, 'withholding reason should be detected')
    assert(facts.adverseFactors.some((item) => item.includes('维修/清洁费用')), 'lease damage defense should be detected')
    assert(facts.claimReadiness.some((item) => item.claim === '返还押金/租金' && item.status === 'high_risk'), 'deposit return should be high risk when damage defense appears')
    assert(issues.some((item) => item.issue === '对方抗辩及反证压力' && item.evidenceGaps.some((gap) => gap.suggestion.includes('直接反证'))), 'lease defense issue should include rebuttal evidence gap')
    assert(analysis.conclusionLevel === 'preliminary_possible', 'lease defense should downgrade conclusion')
    assert(risks.some((item) => item.includes('对方可能抗辩') && item.includes('扣押金')), 'lease defense should be in risks')
  })

  await runCase('虚假宣传消费维权', [
    '商家虚假宣传课程效果，我在平台下单付款了，有宣传截图、订单和客服售后聊天，想退款'
  ], (analysis) => {
    const issues = parse(analysis.issuesJson)
    assert(analysis.caseType === '消费维权纠纷', 'consumer case type mismatch')
    assert(analysis.subType.includes('虚假宣传'), 'consumer subtype should include false advertising')
    assert(issues.some((item) => item.issue === '商家是否构成违约或侵权' && item.evidenceStrength === 'strong'), 'consumer issue should be strong')
  })

  await runCase('劳动合同事实冲突', [
    '公司拖欠工资三个月且未签劳动合同',
    '我后来找到了一张劳动合同照片，月工资8000元，也有考勤打卡'
  ], (analysis) => {
    const facts = JSON.parse(analysis.factsJson)
    const questions = parse(analysis.missingQuestionsJson)
    const risks = parse(analysis.risksJson)
    assert(analysis.caseType === '劳动纠纷', 'conflict case should stay labor')
    assert(Array.isArray(facts.contradictions) && facts.contradictions.length > 0, 'contract conflict should be detected')
    assert(questions.some((item) => item.includes('请先澄清') && item.includes('劳动合同')), 'contract conflict should be asked first')
    assert(risks.some((item) => item.includes('事实冲突') && item.includes('劳动合同')), 'contract conflict should be in risks')
  })
} finally {
  if (server) server.kill()
}
