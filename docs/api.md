# REST API 设计

统一响应：
```json
{ "code": 0, "message": "ok", "data": {} }
```

## Auth
- `POST /api/auth/register`
  - Body: `{ "username": "user", "password": "pass", "displayName": "用户" }`
  - Response: `{ "token": "...", "userId": 1, "username": "user", "roleCode": "USER" }`
- `POST /api/auth/login`
  - Body: `{ "username": "user", "password": "pass" }`
  - Response 同注册。

## Legal Sessions
- `POST /api/legal-sessions`
  - Body: `{ "title": "劳动纠纷咨询" }`
- `GET /api/legal-sessions`
- `GET /api/legal-sessions/{id}`
- `PATCH /api/legal-sessions/{id}`
  - Body: `{ "title": "新标题" }`
- `DELETE /api/legal-sessions/{id}`

## Chat
- `POST /api/chat/messages`
  - Body: `{ "sessionId": 1, "content": "公司拖欠工资三个月且未签合同" }`
- `GET /api/chat/stream/{sessionId}?content=...&access_token=...`
  - SSE events: `message`, `done`

## Case Analysis
- `GET /api/case-analyses/{sessionId}`
  - Response: latest structured analysis with facts, missing questions, evidence, liability, action path, risks, citations.

## Files
- `POST /api/files/upload`
  - Multipart field: `file`
  - Response: metadata and parse placeholder.

## Reports
- `POST /api/reports/{sessionId}`
- `GET /api/reports/{id}/download`
  - Markdown download.

## Knowledge
- `GET /api/legal-articles/{id}`
- `GET /api/legal-cases/similar?caseType=劳动纠纷`
- `POST /api/admin/legal-articles`
- `POST /api/admin/legal-cases`
- `GET|POST|PUT /api/admin/prompt-templates`

## Error Codes
- `400`: 请求参数错误、资源不存在、业务前置条件不满足。
- `401`: 未登录或 Token 无效。
- `403`: 权限不足。
- `500`: 服务端异常。
