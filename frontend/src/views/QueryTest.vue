<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import {
  getTBoxJson, executeDslQuery,
  type SemanticQueryResponse,
} from '../api/query'
import {
  listOntologies, listClasses, listProperties,
  type OntologyListItem, type ClassResponse, type PropertyResponse,
} from '../api/ontology'
import QueryEngine from './ontology/QueryEngine.vue'

const activeTab = ref<'ontology' | 'dsl' | 'sparql'>('ontology')

// ─── Common ───
const ontologies = ref<OntologyListItem[]>([])
const selectedOntologyId = ref('')
const error = ref('')
const loading = ref(false)

onMounted(async () => {
  try {
    ontologies.value = await listOntologies()
    if (ontologies.value.length > 0) {
      selectedOntologyId.value = ontologies.value[0].id
    }
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
})

// ═══════════════ Ontology Query Tab ═══════════════

const ontologyResult = ref<SemanticQueryResponse | null>(null)
const ontologyLoading = ref(false)
const copied = ref(false)

async function fetchOntology() {
  if (!selectedOntologyId.value) return
  ontologyLoading.value = true
  error.value = ''
  try {
    ontologyResult.value = await getTBoxJson(selectedOntologyId.value)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    ontologyLoading.value = false
  }
}

function copyJson() {
  if (!ontologyResult.value) return
  navigator.clipboard.writeText(JSON.stringify(ontologyResult.value, null, 2))
  copied.value = true
  setTimeout(() => copied.value = false, 2000)
}

// ═══════════════ DSL Query Tab ═══════════════

const dslClasses = ref<ClassResponse[]>([])
const dslProperties = ref<PropertyResponse[]>([])
const dslJsonInput = ref('')
const dslJsonError = ref('')
const dslCopied = ref(false)
const dslResult = ref<any>(null)

watch(selectedOntologyId, async (id) => {
  if (!id) return
  try {
    const [cls, props] = await Promise.all([listClasses(id), listProperties(id)])
    dslClasses.value = cls
    dslProperties.value = props
    dslJsonInput.value = buildDslJsonTemplate()
  } catch {
    // ignore
  }
})

function buildDslJsonTemplate() {
  const onto = ontologies.value.find(o => o.id === selectedOntologyId.value)
  const cls = dslClasses.value[0]
  const dataProps = dslProperties.value.filter(p => p.property_type === 'data')
  const objProps = dslProperties.value.filter(p => p.property_type === 'object')

  return JSON.stringify({
    ontology: {
      name: onto?.name || '本体名称',
      namespace: onto?.namespace || '',
      version: onto?.version || '1.0.0',
    },
    query: {
      target: cls?.name || 'ClassName',
      selection: dataProps.length >= 2
        ? [dataProps[0].name, { relation: objProps[0]?.name || 'relation1', nested_fields: [dataProps[1].name] }]
        : dataProps.length === 1
          ? [dataProps[0].name]
          : ['field1', { relation: 'relation1', nested_fields: ['field1', 'field2'] }],
      filter: {
        logic: 'AND',
        conditions: [
          { field: dataProps[0]?.name || 'name', operator: 'CONTAINS', value: 'keyword' },
          objProps.length > 0
            ? {
                logic: 'OR',
                conditions: [
                  { path: [objProps[0].name], field: dataProps[1]?.name || dataProps[0]?.name || 'fieldA', operator: 'EQ', value: 'val1' },
                  objProps.length > 1
                    ? { path: [objProps[0].name, objProps[1].name], field: dataProps[0]?.name || 'fieldB', operator: 'GT', value: 100 }
                    : { path: [objProps[0].name, 'relation2'], field: 'fieldB', operator: 'GT', value: 100 },
                ],
              }
            : {
                logic: 'OR',
                conditions: [
                  { path: ['relation1'], field: 'fieldA', operator: 'EQ', value: 'val1' },
                  { path: ['relation1', 'relation2'], field: 'fieldB', operator: 'GT', value: 100 },
                ],
              },
        ],
      },
      pagination: { page: 1, size: 10 },
    },
  }, null, 2)
}

function resetDslJson() {
  dslJsonInput.value = buildDslJsonTemplate()
  dslJsonError.value = ''
  dslResult.value = null
  error.value = ''
}

function formatDslJson() {
  try {
    const parsed = JSON.parse(dslJsonInput.value)
    dslJsonInput.value = JSON.stringify(parsed, null, 2)
    dslJsonError.value = ''
  } catch {
    dslJsonError.value = 'JSON 格式错误，无法格式化'
  }
}

function copyDslJson() {
  navigator.clipboard.writeText(dslJsonInput.value)
  dslCopied.value = true
  setTimeout(() => dslCopied.value = false, 2000)
}

async function executeDslQueryAction() {
  if (!dslJsonInput.value.trim()) {
    error.value = '请输入 DSL 查询 JSON'
    return
  }
  let req: any
  try {
    req = JSON.parse(dslJsonInput.value)
  } catch (e: any) {
    dslJsonError.value = 'JSON 格式错误：' + e.message
    error.value = 'JSON 格式错误'
    return
  }
  dslJsonError.value = ''

  loading.value = true
  error.value = ''
  dslResult.value = null
  try {
    dslResult.value = await executeDslQuery(req)
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
      <h2>语义查询</h2>
      <p>本体查询 · DSL 查询 · SPARQL 查询</p>
    </div>

    <!-- Ontology selector (shared, compact) -->
    <div class="ontology-bar">
      <span class="ontology-label">本体</span>
      <select v-model="selectedOntologyId" class="form-select ontology-select">
        <option v-for="o in ontologies" :key="o.id" :value="o.id">{{ o.name }}</option>
      </select>
    </div>

    <!-- Tabs -->
    <div class="tab-bar" style="margin-bottom: 20px;">
      <button
        :class="['tab-btn', { active: activeTab === 'ontology' }]"
        @click="activeTab = 'ontology'"
      >本体查询</button>
      <button
        :class="['tab-btn', { active: activeTab === 'dsl' }]"
        @click="activeTab = 'dsl'"
      >DSL 查询</button>
      <button
        :class="['tab-btn', { active: activeTab === 'sparql' }]"
        @click="activeTab = 'sparql'"
      >SPARQL 查询</button>
    </div>

    <!-- ═══════════════ ONTOLOGY QUERY ═══════════════ -->
    <div v-if="activeTab === 'ontology'">
      <div class="card" style="margin-bottom: 20px;">
        <div class="form-actions" style="padding: 12px 0;">
          <button
            class="btn btn-primary"
            :disabled="ontologyLoading || !selectedOntologyId"
            @click="fetchOntology"
          >
            {{ ontologyLoading ? '加载中...' : '获取本体结构' }}
          </button>
        </div>
      </div>

      <div v-if="ontologyResult" class="card">
        <div class="card-header" style="display: flex; align-items: center; justify-content: space-between;">
          <h3 style="margin: 0;">
            本体结构
            <span class="tag tag-blue">{{ ontologyResult.ontology?.name || '' }}</span>
          </h3>
          <button class="btn btn-sm" @click="copyJson">{{ copied ? '已复制' : '复制 JSON' }}</button>
        </div>
        <pre class="json-output">{{ JSON.stringify(ontologyResult, null, 2) }}</pre>
      </div>
    </div>

    <!-- ═══════════════ DSL QUERY ═══════════════ -->
    <div v-if="activeTab === 'dsl'">
      <div class="card" style="margin-bottom: 20px;">
        <div class="form-group">
          <label>DSL 查询 (JSON)</label>
          <div class="hint" style="margin-bottom: 6px;">
            格式: <code>DslQueryRequest</code> — ontology { name, namespace, version }, query { target, alias, select (String | SelectItem), filter (嵌套 logic/conditions, path 支持), pagination }
          </div>
          <div class="hint" style="margin-bottom: 6px;">
            操作符: <code>EQ NEQ GT GTE LT LTE IN NOT_IN BETWEEN LIKE CONTAINS STARTS_WITH ENDS_WITH IS_NULL IS_NOT_NULL</code>
          </div>
          <textarea
            v-model="dslJsonInput"
            class="form-input json-input"
            rows="16"
            spellcheck="false"
            placeholder="输入 DSL 查询 JSON..."
          ></textarea>
          <div v-if="dslJsonError" class="json-error">{{ dslJsonError }}</div>
        </div>
        <div class="form-actions" style="display: flex; gap: 8px; flex-wrap: wrap;">
          <button class="btn btn-primary" :disabled="loading || !dslJsonInput.trim()" @click="executeDslQueryAction">
            {{ loading ? '查询中...' : '▶ 执行查询' }}
          </button>
          <button class="btn btn-secondary" @click="formatDslJson">格式化 JSON</button>
          <button class="btn btn-secondary" @click="copyDslJson">{{ dslCopied ? '✓ 已复制' : '复制' }}</button>
          <button class="btn btn-secondary" @click="resetDslJson">重置模板</button>
        </div>
      </div>

      <!-- DSL results -->
      <div v-if="dslResult" class="card">
        <div class="card-header" style="display: flex; align-items: center; justify-content: space-between;">
          <div>
            <h3 style="margin: 0;">查询结果</h3>
            <div v-if="dslResult.message" class="hint" style="margin-top: 4px;">{{ dslResult.message }}</div>
          </div>
          <span class="tag tag-blue">{{ dslResult.total }} 行</span>
        </div>

        <!-- Generated SQL -->
        <div v-if="dslResult.sql" class="sql-section">
          <div style="padding: 8px 16px; font-size: 13px; font-weight: 600; color: var(--text-muted); border-bottom: 1px solid var(--border);">生成的 SQL</div>
          <pre class="sql-output">{{ dslResult.sql }}</pre>
        </div>

        <!-- Raw JSON response -->
        <div>
          <div style="padding: 8px 16px; font-size: 13px; font-weight: 600; color: var(--text-muted); border-bottom: 1px solid var(--border);">返回 JSON</div>
          <pre class="json-output">{{ JSON.stringify(dslResult, null, 2) }}</pre>
        </div>
      </div>
    </div>

    <!-- ═══════════════ SPARQL QUERY ═══════════════ -->
    <div v-if="activeTab === 'sparql'">
      <QueryEngine :ontology-id="selectedOntologyId" />
    </div>

    <!-- Error -->
    <div v-if="error" class="alert alert-error" style="margin-bottom: 20px;">{{ error }}</div>
  </div>
</template>

<style scoped>
.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--border);
}
.tab-btn {
  padding: 10px 24px;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: color 0.15s, border-color 0.15s;
}
.tab-btn:hover { color: var(--text); }
.tab-btn.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}

/* ── Ontology Bar (compact) ── */
.ontology-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 6px 12px;
  background: var(--bg-muted, #f8fafc);
  border: 1px solid var(--border);
  border-radius: var(--radius, 6px);
}
.ontology-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
  white-space: nowrap;
}
.ontology-select {
  width: 260px;
  padding: 4px 8px;
  font-size: 13px;
}

/* ── JSON Input ── */
.json-input {
  font-family: ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
  min-height: 200px;
}
.json-error {
  margin-top: 4px;
  font-size: 12px;
  color: var(--danger, #e53e3e);
}
</style>
