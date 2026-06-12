import type { ApiResult } from './types'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export function getToken() {
  return localStorage.getItem('law-agent-token') ?? ''
}

export function setToken(token: string) {
  localStorage.setItem('law-agent-token', token)
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', headers.get('Content-Type') ?? 'application/json')
  }
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const payload = (await response.json()) as ApiResult<T>
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

export function streamChat(sessionId: number, content: string, onMessage: (chunk: string) => void, onDone: () => void) {
  const token = encodeURIComponent(getToken())
  const url = `${API_BASE}/api/chat/stream/${sessionId}?content=${encodeURIComponent(content)}&access_token=${token}`
  const eventSource = new EventSource(url)
  eventSource.addEventListener('message', (event) => onMessage(event.data))
  eventSource.addEventListener('done', () => {
    eventSource.close()
    onDone()
  })
  eventSource.onerror = () => {
    eventSource.close()
    onDone()
  }
  return eventSource
}
