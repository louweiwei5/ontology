<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getOntology, updateOntology, deleteOntology, exportOntology, exportOwlOntology,
  listClasses, createClass, updateClass, deleteClass,
  listProperties, createProperty, updateProperty, deleteProperty,
  type OntologyResponse, type ClassResponse, type PropertyResponse,
} from '../../api/ontology'
import { listDbConnections, listTables, getTableDetail, type DbConnectionResponse } from '../../api/dbConnection'
import OntologyGraph from './OntologyGraph.vue'

const route = useRoute()
const router = useRouter()
const ontologyId = route.params.id as string

const activeTab = ref<'ontology' | 'graph' | 'relations'>('ontology')

const ontology = ref<OntologyResponse | null>(null)
const classes = ref<ClassResponse[]>([])
const properties = ref<PropertyResponse[]>([])
const loading = ref(true)
const showExportMenu = ref(false)
const error = ref('')
const editing = ref(false)

const editForm = ref({ name: '', namespace: '', description: '', version: '' })

// ── Class Modal ──
const showClassModal = ref(false)
const classModalMode = ref<'create' | 'edit'>('create')
const classEditId = ref<string | null>(null)
const classForm = ref({ name: '', parent_class_name: '', description: '' })
const classSaving = ref(false)

function openClassCreateModal() {
  classModalMode.value = 'create'
  classEditId.value = null
  classForm.value = { name: '', parent_class_name: '', description: '' }
  showClassModal.value = true
}

function openClassEditModal(cls: ClassResponse) {
  classModalMode.value = 'edit'
  classEditId.value = cls.id
  classForm.value = {
    name: cls.name,
    parent_class_name: cls.parent_class_id
      ? (classes.value.find(c => c.id === cls.parent_class_id)?.name || '')
      : '',
    description: cls.description || '',
  }
  showClassModal.value = true
}

function closeClassModal() {
  showClassModal.value = false
  classEditId.value = null
}

async function saveClass() {
  if (!classForm.value.name.trim()) { alert('请输入类名称'); return }
  classSaving.value = true
  try {
    const data = {
      name: classForm.value.name.trim(),
      description: classForm.value.description.trim() || undefined,
      parent_class_name: classForm.value.parent_class_name.trim() || undefined,
    }
    if (classModalMode.value === 'create') {
      await createClass(ontologyId, data)
    } else {
      await updateClass(ontologyId, classEditId.value!, data)
    }
    classes.value = await listClasses(ontologyId)
    closeClassModal()
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  } finally {
    classSaving.value = false
  }
}

// ── Relationships ──
const editingPropId = ref<string | null>(null)

// ── Modal ──
const showModal = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const modalForm = ref<{
  name: string
  relation_type: string
  domain_class_name: string
  range: string
  description: string
  mapping_rules: { domain_property: string; range_property: string }[]
  junction_db_connection_id: string
  junction_table_name: string
  junction_domain_column: string
  junction_range_column: string
}>({
  name: '',
  relation_type: '',
  domain_class_name: '',
  range: '',
  description: '',
  mapping_rules: [],
  junction_db_connection_id: '',
  junction_table_name: '',
  junction_domain_column: '',
  junction_range_column: '',
})
const modalSaving = ref(false)

const connectionCategory = computed({
  get: () => {
    const rt = modalForm.value.relation_type
    if (rt === 'one-to-one' || rt === 'one-to-many' || rt === 'many-to-one') return 'object'
    if (rt === 'many-to-many') return 'junction'
    return ''
  },
  set: (val: string) => {
    if (val === 'object') {
      const rt = modalForm.value.relation_type
      if (rt !== 'one-to-one' && rt !== 'one-to-many' && rt !== 'many-to-one') {
        modalForm.value.relation_type = 'one-to-one'
      }
    } else if (val === 'junction') {
      modalForm.value.relation_type = 'many-to-many'
    } else {
      modalForm.value.relation_type = ''
    }
  },
})

// ── Database connections for many-to-many ──
const dbConnections = ref<DbConnectionResponse[]>([])
const junctionTables = ref<{ table_name: string; table_comment: string }[]>([])
const junctionColumns = ref<{ column_name: string; column_type: string }[]>([])
const loadingDbTables = ref(false)

async function loadDbConnections() {
  try {
    dbConnections.value = await listDbConnections()
  } catch (e: any) {
    console.warn('Failed to load DB connections:', e)
  }
}

async function onJunctionConnectionChange() {
  junctionTables.value = []
  modalForm.value.junction_table_name = ''
  modalForm.value.junction_domain_column = ''
  modalForm.value.junction_range_column = ''
  junctionColumns.value = []
  if (!modalForm.value.junction_db_connection_id) return
  loadingDbTables.value = true
  try { junctionTables.value = await listTables(modalForm.value.junction_db_connection_id) } catch { /* ignore */ }
  loadingDbTables.value = false
}

async function onJunctionTableChange() {
  modalForm.value.junction_domain_column = ''
  modalForm.value.junction_range_column = ''
  junctionColumns.value = []
  if (!modalForm.value.junction_db_connection_id || !modalForm.value.junction_table_name) return
  try {
    const detail = await getTableDetail(modalForm.value.junction_db_connection_id, modalForm.value.junction_table_name)
    junctionColumns.value = detail.columns.map(c => ({ column_name: c.column_name, column_type: c.column_type }))
  } catch { /* ignore */ }
}

const objectProperties = computed(() =>
  properties.value.filter(p => p.property_type === 'object')
)

function getClassName(classId: string | null | undefined): string {
  if (!classId) return ''
  return classes.value.find(c => c.id === classId)?.name || classId
}

function getDataPropertiesOfClass(className: string) {
  const cls = classes.value.find(c => c.name === className)
  if (!cls) return []
  return properties.value.filter(p => p.domain_class_id === cls.id && p.property_type === 'data')
}

function getDomainDataProperties() {
  return getDataPropertiesOfClass(modalForm.value.domain_class_name)
}

function getRangeDataProperties() {
  return getDataPropertiesOfClass(modalForm.value.range)
}

function openCreateModal() {
  modalMode.value = 'create'
  modalForm.value = {
    name: '', relation_type: '', domain_class_name: '', range: '', description: '',
    mapping_rules: [{ domain_property: '', range_property: '' }],
    junction_db_connection_id: '', junction_table_name: '', junction_domain_column: '', junction_range_column: '',
  }
  junctionTables.value = []
  junctionColumns.value = []
  loadDbConnections()
  showModal.value = true
}

async function openEditModal(p: PropertyResponse) {
  modalMode.value = 'edit'
  junctionTables.value = []
  junctionColumns.value = []
  editingPropId.value = p.id

  await loadDbConnections()

  modalForm.value = {
    name: p.name,
    relation_type: p.relation_type || '',
    domain_class_name: getClassName(p.domain_class_id),
    range: p.range || '',
    description: p.description || '',
    mapping_rules: (p.mapping_rules && p.mapping_rules.length > 0)
      ? p.mapping_rules.map(r => ({ domain_property: r.domain_property, range_property: r.range_property }))
      : [{ domain_property: '', range_property: '' }],
    junction_db_connection_id: p.junction_table_id || '',
    junction_table_name: p.junction_table_name || '',
    junction_domain_column: p.junction_domain_column || '',
    junction_range_column: p.junction_range_column || '',
  }
  showModal.value = true

  // Pre-load junction tables/columns if editing a many-to-many with saved connection
  if (p.junction_table_id && modalForm.value.junction_table_name) {
    loadingDbTables.value = true
    try {
      junctionTables.value = await listTables(modalForm.value.junction_db_connection_id)
      const detail = await getTableDetail(modalForm.value.junction_db_connection_id, modalForm.value.junction_table_name)
      junctionColumns.value = detail.columns.map(c => ({ column_name: c.column_name, column_type: c.column_type }))
    } catch { /* ignore */ }
    loadingDbTables.value = false
  } else if (p.junction_table_id) {
    loadingDbTables.value = true
    try {
      junctionTables.value = await listTables(modalForm.value.junction_db_connection_id)
    } catch { /* ignore */ }
    loadingDbTables.value = false
  }
}

function closeModal() {
  showModal.value = false
  editingPropId.value = null
}

function addMappingRule() {
  modalForm.value.mapping_rules.push({ domain_property: '', range_property: '' })
}

function removeMappingRule(index: number) {
  modalForm.value.mapping_rules.splice(index, 1)
}

async function saveModal() {
  if (!modalForm.value.name.trim()) { alert('请输入关系名称'); return }
  if (!modalForm.value.domain_class_name) { alert('请选择出发类'); return }
  if (!modalForm.value.range) { alert('请选择目标类'); return }

  modalSaving.value = true
  try {
    const payload: any = {
      name: modalForm.value.name.trim(),
      property_type: 'object' as const,
      domain_class_name: modalForm.value.domain_class_name,
      range: modalForm.value.range,
      description: modalForm.value.description.trim() || undefined,
      mapping_rules: modalForm.value.mapping_rules.filter(r => r.domain_property && r.range_property),
    }

    if (modalForm.value.relation_type) {
      payload.relation_type = modalForm.value.relation_type
    }

    if (modalForm.value.relation_type === 'many-to-many') {
      payload.junction_table_id = modalForm.value.junction_db_connection_id || undefined
      payload.junction_table_name = modalForm.value.junction_table_name || undefined
      payload.junction_domain_column = modalForm.value.junction_domain_column || undefined
      payload.junction_range_column = modalForm.value.junction_range_column || undefined
    }

    if (modalMode.value === 'create') {
      await createProperty(ontologyId, payload)
    } else {
      await updateProperty(ontologyId, editingPropId.value!, payload)
    }

    properties.value = await listProperties(ontologyId)
    closeModal()
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  } finally {
    modalSaving.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [o, cls, props] = await Promise.all([
      getOntology(ontologyId),
      listClasses(ontologyId),
      listProperties(ontologyId),
    ])
    ontology.value = o
    classes.value = cls
    properties.value = props
    editForm.value = { name: o.name, namespace: o.namespace, description: o.description || '', version: o.version }
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

function startEdit() { editing.value = true }
function cancelEdit() {
  if (!ontology.value) return
  editForm.value = { name: ontology.value.name, namespace: ontology.value.namespace, description: ontology.value.description || '', version: ontology.value.version }
  editing.value = false
}

async function saveEdit() {
  if (!ontology.value) return
  try {
    ontology.value = await updateOntology(ontologyId, {
      name: editForm.value.name || undefined,
      namespace: editForm.value.namespace || undefined,
      description: editForm.value.description || undefined,
      version: editForm.value.version || undefined,
    })
    editing.value = false
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

async function handleDelete() {
  if (!ontology.value) return
  if (!confirm(`确认永久删除本体 "${ontology.value.name}"？此操作不可恢复。`)) return
  try {
    await deleteOntology(ontologyId)
    router.push('/ontologies')
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

async function handleExport() {
  try {
    const data = await exportOntology(ontologyId)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${data.name}.json`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

async function handleExportOwl(format: 'rdf-xml' | 'turtle') {
  try {
    const blob = await exportOwlOntology(ontologyId, format) as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const ext = format === 'turtle' ? '.ttl' : '.owl'
    a.href = url
    a.download = `${ontology.value?.name || 'ontology'}${ext}`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

async function removeClass(classId: string, name: string) {
  if (!confirm(`确认删除类 "${name}"？`)) return
  try {
    await deleteClass(ontologyId, classId)
    classes.value = await listClasses(ontologyId)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

// ── Relationship CRUD ──

async function removeProp(propId: string, name: string) {
  if (!confirm(`确认删除关系 "${name}"？`)) return
  try {
    await deleteProperty(ontologyId, propId)
    properties.value = await listProperties(ontologyId)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

onMounted(load)
</script>

<template>
  <div v-if="loading" class="loading">加载中...</div>
  <div v-else-if="error" class="alert alert-error">{{ error }}</div>
  <div v-else-if="ontology">
    <div class="page-header" style="display: flex; align-items: flex-start; justify-content: space-between;">
      <div>
        <h2 style="margin-bottom: 4px;">{{ ontology.name }}</h2>
        <p v-if="ontology.description" style="margin: 0; font-size: 13px; color: var(--text-muted);">{{ ontology.description }}</p>
      </div>
      <div style="display: flex; gap: 6px; align-items: center;">
        <button v-if="!editing" class="btn btn-secondary btn-sm" @click="startEdit">✏️ 编辑</button>
        <div class="dropdown" style="position: relative;">
          <button class="btn btn-secondary btn-sm" @click="showExportMenu = !showExportMenu">📤 导出 ▾</button>
          <div v-if="showExportMenu" class="dropdown-menu" style="position: absolute; top: 100%; right: 0; z-index: 100; background: var(--bg); border: 1px solid var(--border); border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); min-width: 160px; margin-top: 4px;">
            <button class="dropdown-item" style="display: block; width: 100%; padding: 8px 16px; text-align: left; border: none; background: none; cursor: pointer; font-size: 13px;" @click="showExportMenu = false; handleExport()">JSON (自定义格式)</button>
            <button class="dropdown-item" style="display: block; width: 100%; padding: 8px 16px; text-align: left; border: none; background: none; cursor: pointer; font-size: 13px;" @click="showExportMenu = false; handleExportOwl('rdf-xml')">RDF/XML (.owl)</button>
            <button class="dropdown-item" style="display: block; width: 100%; padding: 8px 16px; text-align: left; border: none; background: none; cursor: pointer; font-size: 13px;" @click="showExportMenu = false; handleExportOwl('turtle')">Turtle (.ttl)</button>
          </div>
        </div>
        <button class="btn btn-danger btn-sm" @click="handleDelete">🗑️ 删除</button>
      </div>
    </div>

    <!-- Ontology Metadata (compact) -->
    <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 16px; padding: 8px 14px; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; font-size: 13px;">
      <span><strong style="color: var(--text-muted);">版本：</strong> {{ ontology.version }}</span>
      <span style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
        <strong style="color: var(--text-muted);">命名空间：</strong>
        <code style="font-size: 12px;">{{ ontology.namespace }}</code>
      </span>
    </div>
    <!-- Editing form (hidden when not editing) -->
    <div v-if="editing" class="card" style="margin-bottom: 16px; padding: 12px;">
      <div class="form-row">
        <div class="form-group" style="margin-bottom: 8px;"><label>名称</label><input v-model="editForm.name" class="form-input" style="font-size:13px;padding:4px 8px;" /></div>
        <div class="form-group" style="margin-bottom: 8px;"><label>版本</label><input v-model="editForm.version" class="form-input" style="font-size:13px;padding:4px 8px;" /></div>
      </div>
      <div class="form-group" style="margin-bottom: 8px;"><label>命名空间</label><input v-model="editForm.namespace" class="form-input" style="font-size:13px;padding:4px 8px;" /></div>
      <div class="form-group" style="margin-bottom: 0;"><label>描述</label><textarea v-model="editForm.description" class="form-input" style="font-size:13px;padding:4px 8px;min-height:50px;" /></div>
      <div class="form-actions" style="margin-top: 8px;">
        <button class="btn btn-primary btn-sm" @click="saveEdit">保存</button>
        <button class="btn btn-secondary btn-sm" @click="cancelEdit">取消</button>
      </div>
    </div>

    <!-- Tabs -->
    <div style="display: flex; gap: 0; margin-bottom: 20px; border-bottom: 2px solid var(--border);">
      <button
        :class="['tab-btn', { active: activeTab === 'ontology' }]"
        @click="activeTab = 'ontology'"
      >本体</button>
      <button
        :class="['tab-btn', { active: activeTab === 'relations' }]"
        @click="activeTab = 'relations'"
      >关系</button>
      <button
        :class="['tab-btn', { active: activeTab === 'graph' }]"
        @click="activeTab = 'graph'"
      >图形</button>
    </div>

    <!-- Ontology Tab (classes) -->
    <template v-if="activeTab === 'ontology'">
    <!-- Classes -->
    <div class="card">
      <div class="card-header">
        <h3>类 (Classes) <span class="tag tag-green">{{ classes.length }}</span></h3>
        <button class="btn btn-primary btn-sm" @click="openClassCreateModal">➕ 新建类</button>
      </div>

      <div v-if="classes.length === 0" class="empty-state" style="padding: 20px;">
        <p>暂无类，在上方添加</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>IRI</th>
              <th>父类</th>
              <th>属性数</th>
              <th>描述</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cls in classes" :key="cls.id">
              <td>
                <router-link :to="`/ontologies/${ontologyId}/classes/${cls.id}`"
                  style="font-weight: 500; color: var(--accent);">
                  {{ cls.name }}
                </router-link>
              </td>
              <td style="font-size: 12px; max-width: 180px; overflow: hidden; text-overflow: ellipsis;">{{ cls.full_iri }}</td>
              <td>
                <span v-if="cls.parent_class_id" class="tag tag-blue">
                  {{ classes.find(p => p.id === cls.parent_class_id)?.name || '未知' }}
                </span>
                <span v-else style="color: var(--text-muted);">—</span>
              </td>
              <td style="color: var(--text-muted);">—</td>
              <td style="color: var(--text-muted); max-width: 150px; overflow: hidden; text-overflow: ellipsis;">{{ cls.description || '—' }}</td>
              <td class="actions">
                <router-link :to="`/ontologies/${ontologyId}/classes/${cls.id}`" class="btn btn-secondary btn-sm" title="查看属性">🔍 属性</router-link>
                <button class="btn btn-secondary btn-sm" @click="openClassEditModal(cls)">✏️ 编辑</button>
                <button class="btn btn-danger btn-sm" @click="removeClass(cls.id, cls.name)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    </template>

    <!-- Graph Tab -->
    <template v-if="activeTab === 'graph'">
      <OntologyGraph :ontology-id="ontologyId" />
    </template>

    <!-- Relationships Tab -->
    <template v-if="activeTab === 'relations'">
      <div class="card">
        <div class="card-header">
          <h3>关系 (Object Properties) <span class="tag tag-orange">{{ objectProperties.length }}</span></h3>
          <button class="btn btn-primary btn-sm" @click="openCreateModal">➕ 新建关系</button>
        </div>

        <div v-if="objectProperties.length === 0" class="empty-state" style="padding: 20px;">
          <p>暂无对象关系，点击上方按钮创建</p>
        </div>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>属性名</th>
                <th>关系类别</th>
                <th>域类 (Domain)</th>
                <th>目标类 (Range)</th>
                <th>连接属性</th>
                <th>描述</th>
                <th class="actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in objectProperties" :key="p.id">
                <td style="font-weight: 500;">{{ p.name }}</td>
                <td>
                  <span v-if="p.relation_type === 'one-to-one'" class="tag tag-green">1:1</span>
                  <span v-else-if="p.relation_type === 'one-to-many'" class="tag tag-orange">1:N</span>
                  <span v-else-if="p.relation_type === 'many-to-one'" class="tag tag-blue">N:1</span>
                  <span v-else-if="p.relation_type === 'many-to-many'" class="tag tag-purple">N:N</span>
                  <span v-else style="color: var(--text-muted);">—</span>
                </td>
                <td><span class="tag tag-blue">{{ getClassName(p.domain_class_id) }}</span></td>
                <td><span v-if="p.range" class="tag tag-purple">{{ p.range }}</span><span v-else style="color: var(--text-muted);">—</span></td>
                <td>
                  <template v-if="p.relation_type === 'many-to-many'">
                    <span style="font-size: 12px; color: var(--text-muted);">
                      关联表: {{ p.junction_table_name || '—' }}
                      <span v-if="p.junction_domain_column"> ({{ p.junction_domain_column }} ↔ {{ p.junction_range_column }})</span>
                    </span>
                  </template>
                  <span v-else-if="p.mapping_rules && p.mapping_rules.length > 0" style="font-size: 12px; font-family: ui-monospace, monospace; color: var(--text-muted);">
                    {{ p.mapping_rules.map(r => r.domain_property + '=' + r.range_property).join(', ') }}
                  </span>
                  <span v-else style="color: var(--text-muted);">—</span>
                </td>
                <td style="color: var(--text-muted); max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: normal;">{{ p.description || '—' }}</td>
                <td class="actions" style="white-space: nowrap;">
                  <button class="btn btn-secondary btn-sm" @click="openEditModal(p)">✏️ 编辑</button>
                  <button class="btn btn-danger btn-sm" @click="removeProp(p.id, p.name)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Create/Edit Modal -->
      <Teleport to="body">
        <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
          <div class="modal-content" style="width: 540px;">
            <div class="modal-header">
              <h3>{{ modalMode === 'create' ? '新建关系' : '编辑关系' }}</h3>
              <button class="btn btn-icon" @click="closeModal" style="font-size: 20px;">✕</button>
            </div>

            <div class="modal-body">
              <div class="form-group">
                <label>关系名称</label>
                <input v-model="modalForm.name" class="form-input" placeholder="例如：hasDepartment" />
              </div>

              <div class="form-group">
                <label>描述（可选）</label>
                <input v-model="modalForm.description" class="form-input" placeholder="关系描述" />
              </div>

              <!-- Connection category selector -->
              <div class="form-group">
                <label>连接类别</label>
                <div style="display: flex; gap: 20px;">
                  <label class="radio-label" style="display: flex; align-items: center; gap: 4px; cursor: pointer;">
                    <input type="radio" v-model="connectionCategory" value="object" />
                    <span>对象连接</span>
                    <span style="font-size: 11px; color: var(--text-muted); font-weight: normal;">(一对一 / 多对一 / 一对多)</span>
                  </label>
                  <label class="radio-label" style="display: flex; align-items: center; gap: 4px; cursor: pointer;">
                    <input type="radio" v-model="connectionCategory" value="junction" />
                    <span>关系表连接</span>
                    <span style="font-size: 11px; color: var(--text-muted); font-weight: normal;">(多对多)</span>
                  </label>
                </div>
              </div>

              <!-- Sub-type selector for object connection -->
              <template v-if="connectionCategory === 'object'">
                <div class="form-group" style="padding-left: 16px; border-left: 2px solid var(--border); margin-bottom: 8px;">
                  <label style="font-size: 13px; color: var(--text-muted);">关系类型</label>
                  <div style="display: flex; gap: 12px; margin-top: 6px;">
                    <label class="radio-label" style="display: flex; align-items: center; gap: 4px; cursor: pointer;">
                      <input type="radio" v-model="modalForm.relation_type" value="one-to-one" />
                      一对一
                    </label>
                    <label class="radio-label" style="display: flex; align-items: center; gap: 4px; cursor: pointer;">
                      <input type="radio" v-model="modalForm.relation_type" value="many-to-one" />
                      多对一
                    </label>
                    <label class="radio-label" style="display: flex; align-items: center; gap: 4px; cursor: pointer;">
                      <input type="radio" v-model="modalForm.relation_type" value="one-to-many" />
                      一对多
                    </label>
                  </div>
                </div>
              </template>

              <div class="form-row">
                <div class="form-group">
                  <label>出发类 (Domain)</label>
                  <select v-model="modalForm.domain_class_name" class="form-select" @change="() => { modalForm.mapping_rules.forEach(r => { r.domain_property = '' }) }">
                    <option value="">请选择</option>
                    <option v-for="c in classes" :key="c.id" :value="c.name">{{ c.name }}</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>目标类 (Range)</label>
                  <select v-model="modalForm.range" class="form-select" @change="() => { modalForm.mapping_rules.forEach(r => { r.range_property = '' }) }">
                    <option value="">请选择</option>
                    <option v-for="c in classes" :key="c.id" :value="c.name">{{ c.name }}</option>
                  </select>
                </div>
              </div>

              <!-- 连接属性 — moved below domain/range -->
              <div class="form-group">
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                  <label style="margin-bottom: 0;">连接属性</label>
                  <button class="btn btn-secondary btn-sm" @click="addMappingRule">➕ 添加连接属性</button>
                </div>
                <div class="hint" style="margin-top: -4px; margin-bottom: 8px;">
                  {{ connectionCategory === 'junction' ? '指定两个类中分别对应关联表外键字段的属性' : '指定两个对象用哪些属性进行关联' }}
                </div>

                <div v-for="(rule, i) in modalForm.mapping_rules" :key="i"
                  style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px; padding: 8px; background: var(--bg-muted); border-radius: 6px;">
                  <div style="flex: 1;">
                    <div v-if="getDomainDataProperties().length > 0">
                      <select v-model="rule.domain_property" class="form-select" style="width: 100%;">
                        <option value="">选择出发属性</option>
                        <option v-for="dp in getDomainDataProperties()" :key="dp.id" :value="dp.name">{{ dp.name }}</option>
                      </select>
                    </div>
                    <div v-else>
                      <select v-model="rule.domain_property" class="form-select" style="width: 100%;">
                        <option value="">（请先选择出发类）</option>
                      </select>
                    </div>
                  </div>
                  <span style="color: var(--text-muted); font-weight: 500;">=</span>
                  <div style="flex: 1;">
                    <div v-if="getRangeDataProperties().length > 0">
                      <select v-model="rule.range_property" class="form-select" style="width: 100%;">
                        <option value="">选择目标属性</option>
                        <option v-for="rp in getRangeDataProperties()" :key="rp.id" :value="rp.name">{{ rp.name }}</option>
                      </select>
                    </div>
                    <div v-else>
                      <select v-model="rule.range_property" class="form-select" style="width: 100%;">
                        <option value="">（请先选择目标类）</option>
                      </select>
                    </div>
                  </div>
                  <button class="btn btn-danger btn-icon-sm" @click="removeMappingRule(i)" :disabled="modalForm.mapping_rules.length <= 1">✕</button>
                </div>
              </div>

              <!-- Junction table configuration (many-to-many) -->
              <template v-if="connectionCategory === 'junction'">
                <div class="form-group" style="margin-top: 8px; padding: 12px; background: var(--bg-muted); border-radius: 8px;">
                  <label style="font-weight: 600; margin-bottom: 6px;">关系表关联配置</label>

                  <div class="form-group" style="margin-bottom: 8px;">
                    <label>数据库连接</label>
                    <select v-model="modalForm.junction_db_connection_id" class="form-select" @change="onJunctionConnectionChange">
                      <option value="">请选择</option>
                      <option v-for="conn in dbConnections" :key="conn.id" :value="conn.id">{{ conn.name }}</option>
                    </select>
                  </div>

                  <div class="form-group" style="margin-bottom: 8px;">
                    <label>中间关联表</label>
                    <select v-model="modalForm.junction_table_name" class="form-select" @change="onJunctionTableChange" :disabled="!modalForm.junction_db_connection_id">
                      <option value="">请选择</option>
                      <option v-for="t in junctionTables" :key="t.table_name" :value="t.table_name">
                        {{ t.table_name }} {{ t.table_comment ? '(' + t.table_comment + ')' : '' }}
                      </option>
                    </select>
                  </div>

                  <div class="form-row">
                    <div class="form-group">
                      <label>{{ getClassName(modalForm.domain_class_name) || 'Domain' }} 外键列</label>
                      <select v-model="modalForm.junction_domain_column" class="form-select" :disabled="junctionColumns.length === 0">
                        <option value="">请选择</option>
                        <option v-for="col in junctionColumns" :key="col.column_name" :value="col.column_name">
                          {{ col.column_name }} ({{ col.column_type }})
                        </option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label>{{ modalForm.range || 'Range' }} 外键列</label>
                      <select v-model="modalForm.junction_range_column" class="form-select" :disabled="junctionColumns.length === 0">
                        <option value="">请选择</option>
                        <option v-for="col in junctionColumns" :key="col.column_name" :value="col.column_name">
                          {{ col.column_name }} ({{ col.column_type }})
                        </option>
                      </select>
                    </div>
                  </div>
                </div>
              </template>

            </div>

            <div class="modal-footer">
              <button class="btn btn-secondary" @click="closeModal">取消</button>
              <button class="btn btn-primary" :disabled="modalSaving" @click="saveModal">
                {{ modalSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </Teleport>
    </template>

    <!-- Class Create/Edit Modal -->
    <Teleport to="body">
      <div v-if="showClassModal" class="modal-overlay" @click.self="closeClassModal">
        <div class="modal-content" style="width: 420px;">
          <div class="modal-header">
            <h3>{{ classModalMode === 'create' ? '新建类' : '编辑类' }}</h3>
            <button class="btn btn-icon" @click="closeClassModal" style="font-size: 20px;">✕</button>
          </div>

          <div class="modal-body">
            <div class="form-group">
              <label>名称</label>
              <input v-model="classForm.name" class="form-input" placeholder="例如：Person" />
            </div>
            <div class="form-group">
              <label>父类</label>
              <select v-model="classForm.parent_class_name" class="form-select">
                <option value="">无（顶级类）</option>
                <option v-for="c in classes" :key="c.id"
                  :value="c.name"
                  :disabled="classModalMode === 'edit' && c.id === classEditId">
                  {{ c.name }}
                </option>
              </select>
            </div>
            <div class="form-group" style="margin-bottom: 0;">
              <label>描述（可选）</label>
              <input v-model="classForm.description" class="form-input" placeholder="类描述" />
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" @click="closeClassModal">取消</button>
            <button class="btn btn-primary" :disabled="classSaving || !classForm.name.trim()" @click="saveClass">
              {{ classSaving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.tab-btn {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 500;
  background: none;
  border: none;
  color: var(--text-muted);
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: color 0.15s, border-color 0.15s;
  cursor: pointer;
}
.tab-btn:hover { color: var(--text); }
.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

</style>

<style>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal-content {
  background: var(--bg);
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  max-height: 85vh;
  overflow-y: auto;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 0;
}
.modal-header h3 { margin: 0; }
.modal-body { padding: 16px 24px; }
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}
</style>
