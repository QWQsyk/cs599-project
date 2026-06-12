import { spawn } from 'node:child_process'

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function request(path, options = {}) {
  const response = await fetch(`http://localhost:8080${path}`, options)
  const payload = await response.json()
  if (payload.code !== 0) throw new Error(payload.message || path)
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

async function analyze(title, content) {
  const session = await createSession(title)
  const stream = await fetch(`http://localhost:8080/api/chat/stream/${session.id}?content=${encodeURIComponent(content)}`)
  await stream.text()
  return request(`/api/case-analyses/${session.id}`)
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
  const samples = [
    {
      name: 'labor',
      text: '公司拖欠工资三个月且未签劳动合同，我有工资转账记录和考勤打卡。',
      expectedCaseType: '劳动纠纷'
    },
    {
      name: 'loan',
      text: '朋友借钱2万元不还，没有借条但有微信聊天和银行转账记录，对方姓名手机号都有。',
      expectedCaseType: '民间借贷纠纷'
    },
    {
      name: 'lease',
      text: '房东不退押金3000元，我有租赁合同、押金转账记录、退租交接照片和微信聊天。',
      expectedCaseType: '房屋租赁纠纷'
    },
    {
      name: 'consumer',
      text: '商家虚假宣传课程效果，我在平台下单付款了，有宣传截图、订单和客服售后聊天，想退款。',
      expectedCaseType: '消费维权纠纷'
    }
  ]

  for (const sample of samples) {
    const analysis = await analyze(sample.name, sample.text)
    if (analysis.caseType !== sample.expectedCaseType) {
      throw new Error(`${sample.name} expected ${sample.expectedCaseType}, got ${analysis.caseType}`)
    }
    console.log(`PASS ${sample.name}: ${analysis.caseType} / ${analysis.subType} / ${analysis.conclusionLevel}`)
  }
} finally {
  if (server) server.kill()
}
