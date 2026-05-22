<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { importOntology, importOwlOntology } from '../../api/ontology'

const router = useRouter()

const activeTab = ref<'json' | 'owl'>('json')

// ── JSON import ──
const jsonText = ref(`{
  "name": "organization",
  "namespace": "http://example.org/org",
  "description": "组织本体",
  "version": "1.0.0",
  "classes": [
    { "name": "Person", "description": "人" },
    { "name": "Employee", "description": "员工", "parent_class": "Person" },
    { "name": "Department", "description": "部门" }
  ],
  "properties": [
    { "name": "fullName", "property_type": "data", "data_type": "string", "domain_class": "Person" },
    { "name": "worksFor", "property_type": "object", "domain_class": "Employee", "range": "Department" }
  ]
}`)

const loading = ref(false)
const error = ref('')

async function handleJsonImport() {
  error.value = ''
  let data: any
  try {
    data = JSON.parse(jsonText.value)
  } catch {
    error.value = 'JSON 格式错误，请检查语法'
    return
  }

  if (!data.name || !data.name.trim()) {
    error.value = '本体名称不能为空'
    return
  }

  loading.value = true
  try {
    const result = await importOntology(data)
    router.push(`/ontologies/${result.id}`)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

// ── OWL import ──
const owlFile = ref<File | null>(null)

function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  owlFile.value = input.files?.[0] || null
  error.value = ''
}

async function handleOwlImport() {
  if (!owlFile.value) {
    error.value = '请选择 OWL 文件'
    return
  }

  error.value = ''
  loading.value = true
  try {
    const result = await importOwlOntology(owlFile.value)
    router.push(`/ontologies/${result.id}`)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h2>导入本体</h2>
      <p>通过 JSON 或 OWL 文件格式导入本体定义</p>
    </div>

    <!-- Tabs -->
    <div style="display: flex; gap: 0; margin-bottom: 20px; border-bottom: 2px solid var(--border);">
      <button
        :class="['tab-btn', { active: activeTab === 'json' }]"
        @click="activeTab = 'json'"
      >JSON</button>
      <button
        :class="['tab-btn', { active: activeTab === 'owl' }]"
        @click="activeTab = 'owl'"
      >OWL 文件</button>
    </div>

    <div v-if="error" class="alert alert-error">{{ error }}</div>

    <!-- JSON Tab -->
    <div v-if="activeTab === 'json'" class="card" style="max-width: 700px;">
      <form @submit.prevent="handleJsonImport">
        <div class="form-group">
          <label>JSON 数据</label>
          <textarea
            v-model="jsonText"
            class="form-input"
            style="min-height: 280px; font-family: ui-monospace, Consolas, monospace; font-size: 13px;"
          />
          <div class="hint">
            支持 JSON 格式：包含 name, namespace（可选）, classes[].name/parent_class, properties[].name/property_type/domain_class/range
          </div>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">
            {{ loading ? '导入中...' : '导入' }}
          </button>
          <router-link to="/ontologies" class="btn btn-secondary">取消</router-link>
        </div>
      </form>
    </div>

    <!-- OWL Tab -->
    <div v-else class="card" style="max-width: 700px;">
      <form @submit.prevent="handleOwlImport">
        <div class="form-group">
          <label>OWL 文件</label>
          <input
            type="file"
            accept=".owl,.owx,.rdf,.xml,.ttl"
            class="form-input"
            style="padding: 8px;"
            @change="onFileSelected"
          />
          <div class="hint">
            支持 OWL/RDF/XML（Protégé 导出格式，含 .owx）以及 Turtle 格式。文件中的类、属性、层级关系将被解析导入。
          </div>
        </div>
        <div class="inline-form" v-if="owlFile" style="border-bottom: none; padding-bottom: 0; margin-bottom: 0;">
          <div class="form-group" style="flex: 1;">
            <label>已选择文件</label>
            <span style="font-size: 13px; color: var(--text);">{{ owlFile.name }} ({{ (owlFile.size / 1024).toFixed(1) }} KB)</span>
          </div>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading || !owlFile">
            {{ loading ? '导入中...' : '导入' }}
          </button>
          <router-link to="/ontologies" class="btn btn-secondary">取消</router-link>
        </div>
      </form>
    </div>
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
}

.tab-btn:hover {
  color: var(--text);
}

.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}
</style>
