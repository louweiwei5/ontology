import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

// ─── Types ───

export interface OntologyListItem {
  id: string
  name: string
  namespace: string
  description: string | null
  version: string
  class_count: number
  property_count: number
  created_at: string
  updated_at: string
}

export interface OntologyResponse {
  id: string
  name: string
  namespace: string
  description: string | null
  version: string
  created_at: string
  updated_at: string
}

export interface ClassResponse {
  id: string
  ontology_id: string
  name: string
  full_iri: string
  parent_class_id: string | null
  description: string | null
  created_at: string
  updated_at: string
}

export interface MappingRule {
  domain_property: string
  range_property: string
}

export interface PropertyResponse {
  id: string
  ontology_id: string
  name: string
  full_iri: string
  property_type: 'data' | 'object'
  relation_type: 'one-to-one' | 'one-to-many' | 'many-to-one' | 'many-to-many' | null
  data_type: string | null
  domain_class_id: string | null
  range: string | null
  description: string | null
  junction_table_id: string | null
  junction_table_name: string | null
  junction_domain_column: string | null
  junction_range_column: string | null
  mapping_rules: MappingRule[] | null
  primary_key: boolean | null
  created_at: string
  updated_at: string
}

export interface OntologyExport {
  name: string
  namespace: string
  description: string | null
  version: string
  classes: { name: string; description: string | null; parent_class: string | null }[]
  properties: {
    name: string
    property_type: string
    data_type: string | null
    domain_class: string | null
    range: string | null
    description: string | null
  }[]
}

// ─── Ontology API ───

export async function listOntologies() {
  const res = await api.get<OntologyListItem[]>('/ontologies')
  return res.data
}

export async function getOntology(id: string) {
  const res = await api.get<OntologyResponse>(`/ontologies/${id}`)
  return res.data
}

export async function createOntology(data: { name: string; namespace?: string; description?: string; version?: string }) {
  const res = await api.post<OntologyResponse>('/ontologies', data)
  return res.data
}

export async function updateOntology(id: string, data: { name?: string; namespace?: string; description?: string; version?: string }) {
  const res = await api.put<OntologyResponse>(`/ontologies/${id}`, data)
  return res.data
}

export async function deleteOntology(id: string) {
  await api.delete(`/ontologies/${id}`)
}

export async function exportOntology(id: string) {
  const res = await api.get<OntologyExport>(`/ontologies/${id}/export`)
  return res.data
}

export async function exportOwlOntology(id: string, format: 'rdf-xml' | 'turtle' = 'rdf-xml') {
  const res = await api.get(`/ontologies/${id}/export/owl`, {
    params: { format },
    responseType: 'blob',
  })
  return res.data
}

export async function importOntology(data: any) {
  const res = await api.post<OntologyResponse>('/ontologies/import', data)
  return res.data
}

export interface GraphNode {
  id: string
  name: string
  type: string
  dataProperties?: string[]
}

export interface GraphEdge {
  source: string
  target: string
  label: string
  type: string
}

export interface OntologyGraph {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export async function getOntologyGraph(id: string) {
  const res = await api.get<OntologyGraph>(`/ontologies/${id}/graph`)
  return res.data
}

export async function importOwlOntology(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await api.post<OntologyResponse>('/ontologies/import/owl', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}

// ─── Class API ───

export async function listClasses(ontologyId: string) {
  const res = await api.get<ClassResponse[]>(`/ontologies/${ontologyId}/classes`)
  return res.data
}

export async function getClass(ontologyId: string, classId: string) {
  const res = await api.get<ClassResponse>(`/ontologies/${ontologyId}/classes/${classId}`)
  return res.data
}

export async function createClass(ontologyId: string, data: { name: string; description?: string; parent_class_name?: string }) {
  const res = await api.post<ClassResponse>(`/ontologies/${ontologyId}/classes`, data)
  return res.data
}

export async function updateClass(ontologyId: string, classId: string, data: { name?: string; description?: string; parent_class_name?: string }) {
  const res = await api.put<ClassResponse>(`/ontologies/${ontologyId}/classes/${classId}`, data)
  return res.data
}

export async function deleteClass(ontologyId: string, classId: string) {
  await api.delete(`/ontologies/${ontologyId}/classes/${classId}`)
}

// ─── Property API ───

export async function listProperties(ontologyId: string) {
  const res = await api.get<PropertyResponse[]>(`/ontologies/${ontologyId}/properties`)
  return res.data
}

export async function createProperty(ontologyId: string, data: {
  name: string
  property_type: 'data' | 'object'
  relation_type?: string
  data_type?: string
  domain_class_name?: string
  range?: string
  description?: string
  junction_table_id?: string
  junction_table_name?: string
  junction_domain_column?: string
  junction_range_column?: string
}) {
  const res = await api.post<PropertyResponse>(`/ontologies/${ontologyId}/properties`, data)
  return res.data
}

export async function deleteProperty(ontologyId: string, propId: string) {
  await api.delete(`/ontologies/${ontologyId}/properties/${propId}`)
}

export async function updateProperty(ontologyId: string, propId: string, data: {
  name?: string
  property_type?: 'data' | 'object'
  relation_type?: string
  data_type?: string
  domain_class_name?: string
  range?: string
  description?: string
  junction_table_id?: string
  junction_table_name?: string
  junction_domain_column?: string
  junction_range_column?: string
}) {
  const res = await api.put<PropertyResponse>(`/ontologies/${ontologyId}/properties/${propId}`, data)
  return res.data
}
