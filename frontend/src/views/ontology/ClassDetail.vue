<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getOntology, getClass, updateClass,
  listClasses, listProperties,
  createProperty, deleteProperty, updateProperty,
  type ClassResponse, type PropertyResponse,
} from '../../api/ontology'

const route = useRoute()
const router = useRouter()
const ontologyId = route.params.ontologyId as string
const classId = route.params.classId as string

const cls = ref<ClassResponse | null>(null)
const allClasses = ref<ClassResponse[]>([])
const allProperties = ref<PropertyResponse[]>([])
const loading = ref(true)
const error = ref('')
const editing = ref(false)

const editForm = ref({ name: '', description: '', parent_class_name: '' })

const newProp = ref({ name: '', property_type: 'data' as 'data' | 'object', data_type: 'string', range: '', description: '' })
const propCreating = ref(false)

// ── Inline property editing ──
const editingPropId = ref<string | null>(null)
const editingPropForm = ref({ name: '', description: '' })

function startEditProperty(p: PropertyResponse) {
  editingPropId.value = p.id
  editingPropForm.value = { name: p.name, description: p.description || '' }
}

function cancelEditProperty() {
  editingPropId.value = null
  editingPropForm.value = { name: '', description: '' }
}

async function saveEditProperty(p: PropertyResponse) {
  if (!editingPropForm.value.name.trim()) return
  try {
    await updateProperty(ontologyId, p.id, {
      name: editingPropForm.value.name.trim() || undefined,
      description: editingPropForm.value.description.trim() || undefined,
    })
    editingPropId.value = null
    allProperties.value = await listProperties(ontologyId)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

const parentClassName = computed(() => {
  if (!cls.value?.parent_class_id) return null
  return allClasses.value.find(c => c.id === cls.value!.parent_class_id)?.name || '未知'
})

const classProperties = computed(() => {
  if (!cls.value) return []
  return allProperties.value.filter(p => p.domain_class_id === cls.value!.id)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [c, clsList, props] = await Promise.all([
      getClass(ontologyId, classId),
      listClasses(ontologyId),
      listProperties(ontologyId),
    ])
    cls.value = c
    allClasses.value = clsList
    allProperties.value = props
    editForm.value = {
      name: c.name,
      description: c.description || '',
      parent_class_name: parentClassName.value || '',
    }
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

function startEdit() { editing.value = true }
function cancelEdit() {
  if (!cls.value) return
  editForm.value = { name: cls.value.name, description: cls.value.description || '', parent_class_name: parentClassName.value || '' }
  editing.value = false
}

async function saveEdit() {
  if (!cls.value) return
  try {
    cls.value = await updateClass(ontologyId, classId, {
      name: editForm.value.name || undefined,
      description: editForm.value.description || undefined,
      parent_class_name: editForm.value.parent_class_name || undefined,
    })
    editing.value = false
    allClasses.value = await listClasses(ontologyId)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

async function addProperty() {
  if (!newProp.value.name.trim()) return
  propCreating.value = true
  try {
    const payload: any = {
      name: newProp.value.name.trim(),
      property_type: newProp.value.property_type,
      domain_class_name: cls.value!.name,
    }
    if (newProp.value.property_type === 'data') {
      payload.data_type = newProp.value.data_type
    } else {
      payload.range = newProp.value.range.trim() || undefined
    }
    payload.description = newProp.value.description.trim() || undefined
    await createProperty(ontologyId, payload)
    newProp.value = { name: '', property_type: 'data', data_type: 'string', range: '', description: '' }
    allProperties.value = await listProperties(ontologyId)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  } finally {
    propCreating.value = false
  }
}

async function removeProperty(propId: string, name: string) {
  if (!confirm(`确认删除属性 "${name}"？`)) return
  try {
    await deleteProperty(ontologyId, propId)
    allProperties.value = await listProperties(ontologyId)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

onMounted(load)
</script>

<template>
  <div v-if="loading" class="loading">加载中...</div>
  <div v-else-if="error" class="alert alert-error">{{ error }}</div>
  <div v-else-if="cls">
    <div class="page-header" style="display: flex; align-items: flex-start; justify-content: space-between;">
      <div>
        <router-link :to="`/ontologies/${ontologyId}`" style="font-size: 13px; color: var(--accent); display: inline-block; margin-bottom: 4px;">
          ← 返回本体
        </router-link>
        <h2 style="margin-bottom: 4px;">{{ cls.name }}</h2>
        <p v-if="cls.description" style="margin: 0; font-size: 13px; color: var(--text-muted);">{{ cls.description }}</p>
      </div>
      <div style="display: flex; gap: 6px;">
        <button v-if="!editing" class="btn btn-secondary btn-sm" @click="startEdit">✏️ 编辑</button>
      </div>
    </div>

    <!-- Class Metadata (compact) -->
    <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 16px; padding: 8px 14px; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; font-size: 13px;">
      <span><strong style="color: var(--text-muted);">父类：</strong>
        <span v-if="parentClassName" class="tag tag-blue" style="font-size:12px;">{{ parentClassName }}</span>
        <span v-else style="color: var(--text-muted);">—</span>
      </span>
      <span style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
        <strong style="color: var(--text-muted);">IRI：</strong>
        <code style="font-size: 12px;">{{ cls.full_iri }}</code>
      </span>
    </div>

    <!-- Editing form -->
    <div v-if="editing" class="card" style="margin-bottom: 16px; padding: 12px;">
      <div class="form-group" style="margin-bottom: 8px;"><label>名称</label><input v-model="editForm.name" class="form-input" style="font-size:13px;padding:4px 8px;" /></div>
      <div class="form-group" style="margin-bottom: 8px;">
        <label>父类</label>
        <select v-model="editForm.parent_class_name" class="form-select" style="font-size:13px;padding:4px 8px;">
          <option value="">无（顶级类）</option>
          <option v-for="c in allClasses" :key="c.id" :value="c.name" :disabled="c.id === classId">{{ c.name }}</option>
        </select>
      </div>
      <div class="form-group" style="margin-bottom: 0;"><label>描述</label><textarea v-model="editForm.description" class="form-input" style="font-size:13px;padding:4px 8px;min-height:50px;" /></div>
      <div class="form-actions" style="margin-top: 8px;">
        <button class="btn btn-primary btn-sm" @click="saveEdit">保存</button>
        <button class="btn btn-secondary btn-sm" @click="cancelEdit">取消</button>
      </div>
    </div>

    <!-- Properties -->
    <div class="card">
      <div class="card-header">
        <h3>属性 (Properties) <span class="tag tag-orange">{{ classProperties.length }}</span></h3>
      </div>

      <div class="inline-form">
        <div class="form-group">
          <label>名称</label>
          <input v-model="newProp.name" class="form-input" placeholder="例如：age" />
        </div>
        <div class="form-group" style="flex: 0.7;">
          <label>类型</label>
          <select v-model="newProp.property_type" class="form-select">
            <option value="data">Data</option>
            <option value="object">Object</option>
          </select>
        </div>
        <template v-if="newProp.property_type === 'data'">
          <div class="form-group" style="flex: 0.8;">
            <label>数据类型</label>
            <select v-model="newProp.data_type" class="form-select">
              <option value="string">字符串 (string)</option>
              <option value="integer">整数 (integer)</option>
              <option value="float">浮点数 (float)</option>
              <option value="boolean">布尔 (boolean)</option>
              <option value="date">日期 (date)</option>
              <option value="datetime">日期时间 (datetime)</option>
              <option value="text">长文本 (text)</option>
            </select>
          </div>
        </template>
        <template v-else>
          <div class="form-group" style="flex: 0.7;">
            <label>Range 类</label>
            <input v-model="newProp.range" class="form-input" placeholder="目标类名" list="class-list" />
          </div>
        </template>
        <div class="form-group" style="flex: 0.6;">
          <label>描述</label>
          <input v-model="newProp.description" class="form-input" placeholder="可选" />
        </div>
        <button class="btn btn-primary btn-sm" :disabled="propCreating || !newProp.name.trim()" @click="addProperty">
          {{ propCreating ? '...' : '添加' }}
        </button>
      </div>

      <div v-if="classProperties.length === 0" class="empty-state" style="padding: 20px;">
        <p>暂无属性，在上方添加</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>IRI</th>
              <th>类型</th>
              <th>数据类型 / Range</th>
              <th>描述</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in classProperties" :key="p.id">
              <td style="font-weight: 500;">
                <template v-if="editingPropId === p.id">
                  <input v-model="editingPropForm.name" class="form-input" style="width: 100%;" />
                </template>
                <template v-else>{{ p.name }}</template>
              </td>
              <td style="font-size: 12px; max-width: 180px; overflow: hidden; text-overflow: ellipsis;">{{ p.full_iri }}</td>
              <td>
                <span v-if="p.property_type === 'data'" class="tag tag-green">data</span>
                <span v-else class="tag tag-orange">object</span>
              </td>
              <td>
                <span v-if="p.data_type" class="tag tag-blue">{{ p.data_type }}</span>
                <span v-else-if="p.range" class="tag tag-purple">{{ p.range }}</span>
                <span v-else style="color: var(--text-muted);">—</span>
              </td>
              <td style="max-width: 200px;">
                <template v-if="editingPropId === p.id">
                  <input v-model="editingPropForm.description" class="form-input" style="width: 100%;" placeholder="描述" />
                </template>
                <template v-else>
                  <span style="color: var(--text-muted); white-space: normal; display: block;">{{ p.description || '—' }}</span>
                </template>
              </td>
              <td class="actions" style="white-space: nowrap;">
                <template v-if="editingPropId === p.id">
                  <button class="btn btn-primary btn-sm" @click="saveEditProperty(p)">保存</button>
                  <button class="btn btn-secondary btn-sm" @click="cancelEditProperty">取消</button>
                </template>
                <template v-else>
                  <button class="btn btn-secondary btn-sm" @click="startEditProperty(p)" title="编辑">✏️</button>
                  <button class="btn btn-danger btn-icon-sm" @click="removeProperty(p.id, p.name)" title="删除">✕</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
