# Demo 与测试记录

## 1. Demo 启动

### 1.1 完整环境

```powershell
docker compose up
```

访问：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`

### 1.2 轻量演示

```powershell
.\demo\run-demo.ps1
```

该脚本启动 `demo/backend/server.mjs` 和 `demo/frontend/server.mjs`，用于课堂展示和无数据库环境下的快速验证。

## 2. 演示账号

可以在页面直接注册测试账号，例如：

```text
用户名：demo
密码：demo123456
昵称：演示用户
```

## 3. 演示案例一：劳动纠纷

输入：

```text
公司拖欠工资三个月且未签劳动合同，我有工资流水和工作群聊天记录，老板一直说下个月发。
```

预期展示：

- 案件识别为劳动纠纷。
- 子类型包含拖欠工资或未签劳动合同。
- 输出劳动关系、拖欠工资、未签书面劳动合同等争点。
- 证据清单中识别工资流水、工作群聊天记录。
- 风险提示中包含仲裁时效、证据固定等内容。
- 回复包含“仅供初步参考，不构成正式法律意见”。

## 4. 演示案例二：民间借贷

输入：

```text
朋友借钱2万元不还，没有借条但有微信聊天和银行转账记录，对方现在不回消息。
```

预期展示：

- 案件识别为民间借贷纠纷。
- 子类型为无借条但有转账。
- 争点包含借贷合意是否成立。
- 证据评估中转账记录为已出现线索，借条为建议补充或缺失。
- 风险提示中包含对方失联、身份信息和送达线索。

## 5. 报告生成演示

1. 在聊天页完成任一案例分析。
2. 进入报告页或点击生成报告按钮。
3. 调用 `POST /api/reports/{sessionId}`。
4. 下载 Markdown 报告。
5. 检查报告包含案件类型、事实摘要、争点分析、证据建议、维权路径和风险提示。

## 6. 自动化测试

### 6.1 后端测试

```powershell
cd backend
mvn test
```

重点用例：

- `classifiesLaborDisputeAndAsksMissingQuestions`
- `adaptsToLoanDisputeWithDifferentIssues`

### 6.2 前端构建

```powershell
cd frontend
npm install
npm run build
```

构建通过说明 Vue 页面、Pinia Store、路由和 API 类型没有明显编译错误。

## 7. 课堂展示话术

1. 介绍项目目标：普通用户法律咨询不是直接问“能不能赢”，而是先整理事实和证据。
2. 展示登录和会话列表，说明系统是有状态的 Agent 应用。
3. 输入劳动纠纷案例，展示流式输出和结构化面板。
4. 指出 Agent 如何先追问缺失信息，再给出争点、证据和路径。
5. 生成报告，说明同一份 AgentResult 被前端和报告服务复用。
6. 说明扩展方向：真实 LLM、Function Calling、Agentic RAG、OCR 证据抽取、LLMOps 监控。
