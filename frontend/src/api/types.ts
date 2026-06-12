export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface AuthResponse {
  token: string
  userId: number
  username: string
  roleCode: string
}

export interface LegalSession {
  id: number
  userId: number
  title: string
  caseType?: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  userId: number
  role: 'USER' | 'ASSISTANT'
  content: string
  metadata: string
  createdAt: string
}

export interface SessionDetail {
  session: LegalSession
  messages: ChatMessage[]
}

export interface CaseAnalysis {
  id: number
  sessionId: number
  caseType: string
  subType?: string
  claimGoalsJson?: string
  completenessScore?: number
  conclusionLevel?: string
  factsJson: string
  missingQuestionsJson: string
  issuesJson?: string
  evidenceAssessmentsJson?: string
  evidenceJson: string
  liabilityJson: string
  actionPathJson: string
  risksJson: string
  citationsJson: string
  updatedAt: string
}

export interface AnalysisReport {
  id: number
  sessionId: number
  userId: number
  title: string
  contentMd: string
  createdAt: string
}
