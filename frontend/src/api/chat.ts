import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 120000 })

export interface QueryResult {
  columns: string[]
  rows: Record<string, any>[]
  total: number
  message?: string
  sql?: string
  dsl_error?: string
  field_labels?: Record<string, string>
  relation_types?: Record<string, string>
}

export interface ChatResponse {
  reply: string
  dsl_query: Record<string, any> | null
  query_result: QueryResult | null
  session_id: string
  message_id: string
  llm_prompt?: LlmMessage[]
}

export interface LlmMessage {
  role: string
  content: string
}

export interface ChatMessage {
  id: string
  role: string
  content: string
  dsl_query: Record<string, any> | null
  query_result: QueryResult | null
  llm_prompt?: LlmMessage[]
  created_at: string
}

export interface ChatSession {
  session_id: string
  preview: string
  message_count: number
  created_at: string
  last_message_at: string
}

export async function sendMessage(agentId: string, message: string, sessionId?: string) {
  const res = await api.post<ChatResponse>(`/agents/${agentId}/chat`, {
    message,
    session_id: sessionId,
  })
  return res.data
}

export async function getSessions(agentId: string) {
  const res = await api.get<ChatSession[]>(`/agents/${agentId}/sessions`)
  return res.data
}

export async function getSessionMessages(agentId: string, sessionId: string) {
  const res = await api.get<ChatMessage[]>(`/agents/${agentId}/sessions/${sessionId}`)
  return res.data
}

export async function deleteSession(agentId: string, sessionId: string) {
  await api.delete(`/agents/${agentId}/sessions/${sessionId}`)
}
