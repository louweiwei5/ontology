import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

export interface AgentListItem {
  id: string
  name: string
  description: string | null
  ontology_id: string | null
  ontology_name: string | null
  model_config_id: string | null
  model_config_name: string | null
  created_at: string
  updated_at: string
}

export interface AgentDetail {
  id: string
  name: string
  description: string | null
  system_prompt: string | null
  ontology_id: string | null
  ontology_name: string | null
  model_config_id: string | null
  model_config_name: string | null
  created_at: string
  updated_at: string
}

export async function listAgents() {
  const res = await api.get<AgentListItem[]>('/agents')
  return res.data
}

export async function getAgent(id: string) {
  const res = await api.get<AgentDetail>(`/agents/${id}`)
  return res.data
}

export async function createAgent(data: {
  name: string
  description?: string
  system_prompt?: string
  ontology_id?: string
  model_config_id?: string
}) {
  const res = await api.post<AgentDetail>('/agents', data)
  return res.data
}

export async function updateAgent(id: string, data: {
  name?: string
  description?: string
  system_prompt?: string
  ontology_id?: string
  model_config_id?: string
}) {
  const res = await api.put<AgentDetail>(`/agents/${id}`, data)
  return res.data
}

export async function deleteAgent(id: string) {
  await api.delete(`/agents/${id}`)
}
