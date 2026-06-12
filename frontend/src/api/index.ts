import { request } from './client'
import type { AnalysisReport, AuthResponse, CaseAnalysis, LegalSession, SessionDetail } from './types'

export const api = {
  register: (username: string, password: string, displayName?: string) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify({ username, password, displayName }) }),
  login: (username: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  createSession: (title?: string) =>
    request<LegalSession>('/api/legal-sessions', { method: 'POST', body: JSON.stringify({ title }) }),
  listSessions: () => request<LegalSession[]>('/api/legal-sessions'),
  sessionDetail: (id: number) => request<SessionDetail>(`/api/legal-sessions/${id}`),
  renameSession: (id: number, title: string) =>
    request<LegalSession>(`/api/legal-sessions/${id}`, { method: 'PATCH', body: JSON.stringify({ title }) }),
  deleteSession: (id: number) => request<void>(`/api/legal-sessions/${id}`, { method: 'DELETE' }),
  sendMessage: (sessionId: number, content: string) =>
    request('/api/chat/messages', { method: 'POST', body: JSON.stringify({ sessionId, content }) }),
  caseAnalysis: (sessionId: number) => request<CaseAnalysis | null>(`/api/case-analyses/${sessionId}`),
  generateReport: (sessionId: number) => request<AnalysisReport>(`/api/reports/${sessionId}`, { method: 'POST' }),
  uploadFile: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<Record<string, unknown>>('/api/files/upload', {
      method: 'POST',
      headers: {},
      body: form
    })
  }
}
