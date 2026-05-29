import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

export interface ModelConfigResponse {
  id: string
  name: string
  provider: string
  base_url: string
  api_key_masked: string
  model_name: string
  created_at: string
  updated_at: string
}

export async function listModelConfigs() {
  const res = await api.get<ModelConfigResponse[]>('/model-configs')
  return res.data
}

export async function getModelConfig(id: string) {
  const res = await api.get<ModelConfigResponse>(`/model-configs/${id}`)
  return res.data
}

export async function createModelConfig(data: {
  name: string
  provider: string
  base_url: string
  api_key: string
  model_name: string
}) {
  const res = await api.post<ModelConfigResponse>('/model-configs', data)
  return res.data
}

export async function updateModelConfig(id: string, data: {
  name?: string
  provider?: string
  base_url?: string
  api_key?: string
  model_name?: string
}) {
  const res = await api.put<ModelConfigResponse>(`/model-configs/${id}`, data)
  return res.data
}

export async function deleteModelConfig(id: string) {
  await api.delete(`/model-configs/${id}`)
}
