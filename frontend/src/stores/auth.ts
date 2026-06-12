import { defineStore } from 'pinia'
import { api } from '../api'
import { setToken } from '../api/client'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('law-agent-token') ?? '',
    username: localStorage.getItem('law-agent-username') ?? ''
  }),
  actions: {
    async login(username: string, password: string) {
      const auth = await api.login(username, password)
      setToken(auth.token)
      localStorage.setItem('law-agent-username', auth.username)
      this.token = auth.token
      this.username = auth.username
    },
    async register(username: string, password: string, displayName?: string) {
      const auth = await api.register(username, password, displayName)
      setToken(auth.token)
      localStorage.setItem('law-agent-username', auth.username)
      this.token = auth.token
      this.username = auth.username
    },
    logout() {
      localStorage.removeItem('law-agent-token')
      localStorage.removeItem('law-agent-username')
      this.token = ''
      this.username = ''
    }
  }
})
