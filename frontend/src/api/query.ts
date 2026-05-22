import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 30000 })

// ─── New JSON-based query types ───

export interface WhereCondition {
  field: string
  op: string
  value: any
}

export interface RelationQuery {
  property: string
  select?: string[]
}

export interface SemanticQueryRequest {
  type: 'ontology' | 'instance'
  ontology_id: string
  class_name?: string
  select?: string[]
  where?: WhereCondition[]
  relation?: RelationQuery
  limit?: number
  offset?: number
}

export interface SemanticQueryResponse {
  type: 'ontology' | 'instance'
  class_name?: string
  relation?: { property: string; class: string; columns?: string[] }
  columns: string[]
  rows: Record<string, any>[]
  total: number
  ontology?: {
    name: string
    namespace: string
    version: string
    description: string | null
  }
  classes?: TBoxClass[]
  relationships?: RelationshipItem[]
  message?: string
}

export interface TBoxDataProperty {
  name: string
  data_type: string
  description: string | null
}

export interface TBoxClass {
  name: string
  description: string | null
  parent: string | null
  data_properties: TBoxDataProperty[]
}

export interface RelationshipItem {
  name: string
  relation_type: string | null
  source_class: string | null
  target_class: string | null
  description: string | null
  mapping_rules?: { domain_property: string; range_property: string }[]
}

// ─── New unified query API ───

export async function executeSemanticQuery(req: SemanticQueryRequest): Promise<SemanticQueryResponse> {
  const res = await api.post<SemanticQueryResponse>('/query', req)
  return res.data
}

// ─── Old API (keep for compat, but redirect to new) ───

export interface QueryRequest {
  ontology_id: string
  dsl: string
  db_connection_id?: string
}

export interface QueryResponse {
  format?: string
  columns: string[]
  rows: Record<string, any>[]
  total: number
  data?: Record<string, any>[]
  message?: string
}

export async function executeQuery(req: QueryRequest): Promise<QueryResponse> {
  const res = await api.post<QueryResponse>('/query', req)
  return res.data
}

export async function getTBox(ontologyId: string, format: string = 'markdown'): Promise<string> {
  const res = await api.get<string>(`/ontologies/${ontologyId}/tbox`, { params: { format }, responseType: 'text' })
  return res.data
}

export async function getTBoxJson(ontologyId: string): Promise<SemanticQueryResponse> {
  const res = await api.get<SemanticQueryResponse>(`/ontologies/${ontologyId}/tbox`, { params: { format: 'json' } })
  return res.data
}

export interface RelationQueryRequest {
  ontology_id: string
  source_class: string
  property_name: string
  source_filters?: Record<string, string>
  limit?: number
}

export async function queryRelations(req: RelationQueryRequest): Promise<QueryResponse> {
  const res = await api.post<QueryResponse>('/query/relations', req)
  return res.data
}

export async function executeSparql(ontologyId: string, query: string): Promise<QueryResponse> {
  const res = await api.post<QueryResponse>(`/ontologies/${ontologyId}/sparql`, { query })
  return res.data
}

// ─── DSL Query types ───

export interface DslQueryRequest {
  ontology_id: string
  query: {
    name?: string
    description?: string
    subject: DslSubject
    projection?: DslProjection[]
    filters?: DslFilterGroup
    traversal?: DslTraversal[]
    orderBy?: DslOrderBy[]
    pagination?: DslPagination
    distinct?: boolean
  }
}

export interface DslSubject {
  entity: string
  alias?: string
  id?: string | string[]
}

export interface DslProjection {
  entity?: string
  property: string
  alias?: string
  aggregation?: 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'COUNT_DISTINCT'
  expression?: string
}

export interface DslFilterGroup {
  logic?: 'AND' | 'OR'
  conditions?: DslFilterCondition[]
  groups?: DslFilterGroup[]
}

export interface DslFilterCondition {
  entity?: string
  property: string
  operator: string
  value?: any
  valueType?: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN'
}

export interface DslTraversal {
  from: string
  to: string
  relation: string
  direction?: 'OUT' | 'IN' | 'BOTH'
  cardinality?: 'ONE' | 'MANY'
  optional?: boolean
  filters?: DslFilterGroup
}

export interface DslOrderBy {
  property: string
  direction?: 'ASC' | 'DESC'
  nulls?: 'FIRST' | 'LAST'
}

export interface DslPagination {
  page?: number
  pageSize?: number
  offset?: number
  limit?: number
}

export async function executeDslQuery(req: DslQueryRequest): Promise<SemanticQueryResponse> {
  const res = await api.post<SemanticQueryResponse>('/dsl/query', req)
  return res.data
}
