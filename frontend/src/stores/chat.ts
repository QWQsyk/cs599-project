import { defineStore } from 'pinia'
import { api } from '../api'
import type { CaseAnalysis, ChatMessage, LegalSession } from '../api/types'

export const useChatStore = defineStore('chat', {
  state: () => ({
    sessions: [] as LegalSession[],
    activeSessionId: 0,
    messages: [] as ChatMessage[],
    analysis: null as CaseAnalysis | null,
    loading: false
  }),
  getters: {
    activeSession: (state) => state.sessions.find((item) => item.id === state.activeSessionId)
  },
  actions: {
    async bootstrap() {
      this.sessions = await api.listSessions()
      if (!this.sessions.length) {
        await this.createSession()
      } else {
        await this.openSession(this.sessions[0].id)
      }
    },
    async createSession() {
      const session = await api.createSession('新的法律咨询')
      this.sessions.unshift(session)
      await this.openSession(session.id)
    },
    async openSession(id: number) {
      this.activeSessionId = id
      const detail = await api.sessionDetail(id)
      this.messages = detail.messages
      this.analysis = await api.caseAnalysis(id)
    },
    async renameSession(id: number, title: string) {
      const updated = await api.renameSession(id, title)
      this.sessions = this.sessions.map((item) => (item.id === id ? updated : item))
    },
    async deleteSession(id: number) {
      await api.deleteSession(id)
      this.sessions = this.sessions.filter((item) => item.id !== id)
      if (this.activeSessionId === id) {
        if (this.sessions.length) await this.openSession(this.sessions[0].id)
        else await this.createSession()
      }
    },
    async refreshAnalysis() {
      if (this.activeSessionId) this.analysis = await api.caseAnalysis(this.activeSessionId)
    }
  }
})
