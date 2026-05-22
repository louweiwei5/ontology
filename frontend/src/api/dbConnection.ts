import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

// ─── Types ───

export interface DbConnectionResponse {
  id: string
  name: string
  description: string | null
  db_type: string
  host: string
  port: number
  database_name: string
  username: string
  created_at: string
  updated_at: string
}

export interface TableInfo {
  table_name: string
  table_comment: string
}

export interface ColumnInfo {
  column_name: string
  column_type: string
  is_nullable: boolean
  column_comment: string
  is_primary_key: boolean
}

export interface TableImportResponse {
  id: string
  db_connection_id: string
  ontology_id: string
  status: string
  mapping_json: string
  created_at: string
  updated_at: string
}

export interface TableImportListItem {
  id: string
  db_connection_id: string
  ontology_id: string
  status: string
  connection_name: string
  ontology_name: string
  created_at: string
}

// ─── Connection API ───

export async function createDbConnection(data: {
  name: string; description?: string; host: string; port?: number;
  database_name: string; username: string; password: string
}) {
  const res = await api.post<DbConnectionResponse>('/db-connections', data)
  return res.data
}

export async function listDbConnections() {
  const res = await api.get<DbConnectionResponse[]>('/db-connections')
  return res.data
}

export async function getDbConnection(id: string) {
  const res = await api.get<DbConnectionResponse>(`/db-connections/${id}`)
  return res.data
}

export async function updateDbConnection(id: string, data: Partial<{
  name: string; description: string; host: string; port: number;
  database_name: string; username: string; password: string
}>) {
  const res = await api.put<DbConnectionResponse>(`/db-connections/${id}`, data)
  return res.data
}

export async function deleteDbConnection(id: string) {
  await api.delete(`/db-connections/${id}`)
}

export async function testDbConnection(id: string) {
  const res = await api.post<{ success: boolean; message: string }>(`/db-connections/${id}/test`)
  return res.data
}

export async function listTables(connId: string) {
  const res = await api.get<TableInfo[]>(`/db-connections/${connId}/tables`)
  return res.data
}

export interface TableDetail {
  table_name: string
  table_comment: string
  columns: ColumnInfo[]
}

export async function getTableDetail(connId: string, tableName: string) {
  const res = await api.get<TableDetail>(`/db-connections/${connId}/tables/${tableName}`)
  return res.data
}

// ─── Table Import API ───

export async function createTableImport(connId: string, data: { ontology_id: string; tables: string[] }) {
  const res = await api.post<TableImportResponse>(`/db-connections/${connId}/import`, data)
  return res.data
}

export async function listTableImports() {
  const res = await api.get<TableImportListItem[]>('/db-connections/imports')
  return res.data
}

export async function getTableImport(id: string) {
  const res = await api.get<TableImportResponse>(`/db-connections/imports/${id}`)
  return res.data
}

export async function applyTableImport(id: string) {
  const res = await api.post<TableImportResponse>(`/db-connections/imports/${id}/apply`)
  return res.data
}
