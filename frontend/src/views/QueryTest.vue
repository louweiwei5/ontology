<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import {
  executeSemanticQuery, getTBoxJson, executeDslQuery,
  type SemanticQueryResponse,
} from '../api/query'
import {
  listOntologies, listClasses, listProperties,
  type OntologyListItem, type ClassResponse, type PropertyResponse,
} from '../api/ontology'
import QueryEngine from './ontology/QueryEngine.vue'

const activeTab = ref<'ontology' | 'instance' | 'sparql' | 'dsl'>('ontology')

// ─── Common ───
const ontologies = ref<OntologyListItem[]>([])
const selectedOntologyId = ref('')
const error = ref('')
const loading = ref(false)
const responseData = ref<SemanticQueryResponse | null>(null)

onMounted(async () => {
  try {
    ontologies.value = await listOntologies()
    if (ontologies.value.length > 0) selectedOntologyId.value = ontologies.value[0].id
    // Initialize DSL template
    dslJsonInput.value = buildDslJsonTemplate()
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
})

// ═══════════════ Ontology Query Tab ═══════════════

const ontologyResult = ref<SemanticQueryResponse | null>(null)
const ontologyLoading = ref(false)
const copied = ref(false)

watch(selectedOntologyId, () => {
  ontologyResult.value = null
  responseData.value = null
})

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

// ═══════════════ Instance Query Tab ═══════════════

const classes = ref<ClassResponse[]>([])
const properties = ref<PropertyResponse[]>([])

// Query mode: form | json
const instanceQueryMode = ref<'form' | 'json'>('form')

// Form fields
const selectedClassName = ref('')
const selectedProperties = ref<string[]>([])
const filters = ref<{ field: string; op: string; value: string }[]>([])
const withRelation = ref(false)
const selectedRelationProp = ref('')
const relationSelectProps = ref<string[]>([])
const queryLimit = ref(100)
const queryOffset = ref(0)
const instanceResult = ref<SemanticQueryResponse | null>(null)

// JSON input fields
const jsonInput = ref('')
const jsonError = ref('')
const jsonCopied = ref(false)

function buildJsonTemplate() {
  const ontoId = selectedOntologyId.value || '<本体ID>'
  const clsName = selectedClassName.value || 'ClassName'
  const selProps = selectedProperties.value.length > 0
    ? JSON.stringify(selectedProperties.value, null, 2).replace(/\n {2}/g, '\n  ')
    : '["prop1", "prop2"]'
  return `{\n  "type": "instance",\n  "ontology_id": "${ontoId}",\n  "class_name": "${clsName}",\n  "select": ${selProps},\n  "where": [\n    {"field": "prop", "op": "=", "value": "val"}\n  ],\n  "relation": {\n    "property": "对象属性名",\n    "select": ["目标类字段1"]\n  },\n  "limit": ${queryLimit.value},\n  "offset": ${queryOffset.value}\n}`
}

watch(selectedOntologyId, async (id) => {
  if (!id) return
  try {
    const [cls, props] = await Promise.all([listClasses(id), listProperties(id)])
    classes.value = cls
    properties.value = props
    resetInstanceForm()
  } catch {
    // ignore
  }
})

function resetInstanceForm() {
  selectedClassName.value = ''
  selectedProperties.value = []
  filters.value = []
  withRelation.value = false
  selectedRelationProp.value = ''
  relationSelectProps.value = []
  queryLimit.value = 100
  queryOffset.value = 0
  instanceResult.value = null
  responseData.value = null
  jsonInput.value = ''
  jsonError.value = ''
}

function switchToJsonMode() {
  instanceQueryMode.value = 'json'
  jsonInput.value = buildJsonTemplate()
  jsonError.value = ''
}

function switchToFormMode() {
  instanceQueryMode.value = 'form'
  jsonError.value = ''
}

function formatJson() {
  try {
    const parsed = JSON.parse(jsonInput.value)
    jsonInput.value = JSON.stringify(parsed, null, 2)
    jsonError.value = ''
  } catch {
    jsonError.value = 'JSON 格式错误，无法格式化'
  }
}

function copyJsonInput() {
  navigator.clipboard.writeText(jsonInput.value)
  jsonCopied.value = true
  setTimeout(() => jsonCopied.value = false, 2000)
}

// Data properties of selected class
const dataProperties = computed(() => {
  if (!selectedClassName.value) return []
  const cls = classes.value.find(c => c.name === selectedClassName.value)
  if (!cls) return []
  return properties.value.filter(
    p => p.property_type === 'data' && p.domain_class_id === cls.id
  )
})

// Object properties of selected class
const objectProperties = computed(() => {
  if (!selectedClassName.value) return []
  const cls = classes.value.find(c => c.name === selectedClassName.value)
  if (!cls) return []
  return properties.value.filter(
    p => p.property_type === 'object' && p.domain_class_id === cls.id
  )
})

// Target data properties for selected relation
const relationTargetProperties = computed(() => {
  if (!selectedRelationProp.value) return []
  const prop = properties.value.find(p => p.id === selectedRelationProp.value)
  if (!prop?.range) return []
  const targetCls = classes.value.find(c => c.name === prop.range)
  if (!targetCls) return []
  return properties.value.filter(
    p => p.property_type === 'data' && p.domain_class_id === targetCls.id
  )
})

watch(selectedClassName, () => {
  selectedProperties.value = []
  filters.value = []
  withRelation.value = false
  selectedRelationProp.value = ''
  relationSelectProps.value = []
  instanceResult.value = null
})

function toggleProperty(name: string) {
  const idx = selectedProperties.value.indexOf(name)
  if (idx >= 0) selectedProperties.value.splice(idx, 1)
  else selectedProperties.value.push(name)
}

function addFilter() {
  filters.value.push({ field: '', op: '=', value: '' })
}

function removeFilter(idx: number) {
  filters.value.splice(idx, 1)
}

function toggleRelationTargetProperty(name: string) {
  const idx = relationSelectProps.value.indexOf(name)
  if (idx >= 0) relationSelectProps.value.splice(idx, 1)
  else relationSelectProps.value.push(name)
}

async function executeInstanceQuery() {
  let req: any

  if (instanceQueryMode.value === 'json') {
    if (!jsonInput.value.trim()) {
      error.value = '请输入 JSON 查询'
      return
    }
    try {
      req = JSON.parse(jsonInput.value)
    } catch (e: any) {
      jsonError.value = 'JSON 格式错误：' + e.message
      error.value = 'JSON 格式错误'
      return
    }
    jsonError.value = ''
  } else {
    if (!selectedOntologyId.value || !selectedClassName.value) return

    req = {
      type: 'instance',
      ontology_id: selectedOntologyId.value,
      class_name: selectedClassName.value,
    }

    if (selectedProperties.value.length > 0) {
      req.select = [...selectedProperties.value]
    }

    const validFilters = filters.value.filter(f => f.field && f.value !== '')
    if (validFilters.length > 0) {
      req.where = validFilters.map(f => ({
        field: f.field,
        op: f.op,
        value: f.op.toUpperCase() === 'IN'
          ? f.value.split(',').map(v => v.trim())
          : f.value,
      }))
    }

    if (withRelation.value && selectedRelationProp.value) {
      const prop = properties.value.find(p => p.id === selectedRelationProp.value)
      if (prop) {
        req.relation = { property: prop.name }
        if (relationSelectProps.value.length > 0) {
          req.relation.select = [...relationSelectProps.value]
        }
      }
    }

    if (queryLimit.value) req.limit = queryLimit.value
    if (queryOffset.value) req.offset = queryOffset.value
  }

  loading.value = true
  error.value = ''
  instanceResult.value = null
  try {
    instanceResult.value = await executeSemanticQuery(req)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

// ═══════════════ DSL Query Tab ═══════════════

const dslJsonInput = ref('')
const dslJsonError = ref('')
const dslCopied = ref(false)
const dslResult = ref<SemanticQueryResponse | null>(null)

function buildDslJsonTemplate() {
  const ontoId = selectedOntologyId.value || '本体ID'
  return JSON.stringify({
    ontology_id: ontoId,
    query: {
      subject: { entity: 'Product', alias: '' },
      projection: [
        { property: 'name', alias: '', aggregation: '' },
        { property: 'description', alias: '', aggregation: '' },
      ],
      filters: {
        logic: 'AND',
        conditions: [
          { property: 'name', operator: 'CONTAINS', value: 'DS-2CD', valueType: 'STRING' },
        ],
      },
      orderBy: [
        { property: 'name', direction: 'ASC' },
      ],
      pagination: { page: 1, pageSize: 5 },
      distinct: false,
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
      <p>本体查询 · 实例查询</p>
    </div>

    <!-- Ontology selector (shared) -->
    <div class="card" style="margin-bottom: 20px;">
      <div class="form-group">
        <label>选择本体</label>
        <select v-model="selectedOntologyId" class="form-select" style="max-width: 400px;">
          <option v-for="o in ontologies" :key="o.id" :value="o.id">{{ o.name }}</option>
        </select>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tab-bar" style="margin-bottom: 20px;">
      <button
        :class="['tab-btn', { active: activeTab === 'ontology' }]"
        @click="activeTab = 'ontology'"
      >本体查询</button>
      <button
        :class="['tab-btn', { active: activeTab === 'instance' }]"
        @click="activeTab = 'instance'"
      >实例查询</button>
      <button
        :class="['tab-btn', { active: activeTab === 'sparql' }]"
        @click="activeTab = 'sparql'"
      >SPARQL 查询</button>
      <button
        :class="['tab-btn', { active: activeTab === 'dsl' }]"
        @click="activeTab = 'dsl'"
      >DSL 查询</button>
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

    <!-- ═══════════════ INSTANCE QUERY ═══════════════ -->
    <div v-if="activeTab === 'instance'">
      <div class="card" style="margin-bottom: 20px;">
        <!-- Mode toggle -->
        <div class="form-group">
          <div class="mode-toggle">
            <button
              :class="['mode-btn', { active: instanceQueryMode === 'form' }]"
              @click="switchToFormMode"
            >表单模式</button>
            <button
              :class="['mode-btn', { active: instanceQueryMode === 'json' }]"
              @click="switchToJsonMode"
            >JSON 模式</button>
          </div>
        </div>

        <!-- ═══ Form mode ═══ -->
        <template v-if="instanceQueryMode === 'form'">
          <div class="form-group">
            <label>目标类 (Class)</label>
            <select v-model="selectedClassName" class="form-select" style="max-width: 400px;">
              <option value="" disabled>-- 选择类 --</option>
              <option v-for="c in classes" :key="c.id" :value="c.name">{{ c.name }}</option>
            </select>
          </div>

          <template v-if="selectedClassName">
            <!-- Select properties -->
            <div class="form-group">
              <label>查询属性 <span class="hint">(不选则查询全部)</span></label>
              <div class="checkbox-group">
                <label v-for="dp in dataProperties" :key="dp.name" class="checkbox-item">
                  <input type="checkbox" :checked="selectedProperties.includes(dp.name)" @change="toggleProperty(dp.name)" />
                  <code>{{ dp.name }}</code>
                  <span class="tag tag-green">{{ dp.data_type || 'string' }}</span>
                </label>
                <div v-if="!dataProperties.length" class="hint" style="padding: 8px 0;">该类没有数据属性</div>
              </div>
            </div>

            <!-- Filters -->
            <div class="form-group">
              <div style="display: flex; align-items: center; justify-content: space-between;">
                <label>过滤条件 <span class="hint">(选填)</span></label>
                <button class="btn btn-secondary btn-sm" @click="addFilter">+ 添加条件</button>
              </div>
              <div v-for="(f, i) in filters" :key="i" class="filter-row">
                <select v-model="f.field" class="form-select filter-field">
                  <option value="">选择字段</option>
                  <option v-for="dp in dataProperties" :key="dp.name" :value="dp.name">{{ dp.name }}</option>
                </select>
                <select v-model="f.op" class="form-select filter-op">
                  <option value="=">=</option>
                  <option value="!=">!=</option>
                  <option value=">">&gt;</option>
                  <option value="<">&lt;</option>
                  <option value=">=">&gt;=</option>
                  <option value="<=">&lt;=</option>
                  <option value="LIKE">LIKE</option>
                  <option value="IN">IN</option>
                </select>
                <input v-model="f.value" class="form-input filter-val" :placeholder="f.op === 'IN' ? '逗号分隔多个值' : '值'" />
                <button class="btn btn-danger btn-icon-sm" @click="removeFilter(i)">✕</button>
              </div>
            </div>

            <!-- Relation toggle -->
            <div class="form-group">
              <label class="checkbox-item" style="margin-bottom: 8px;">
                <input type="checkbox" v-model="withRelation" />
                关联查询 (JOIN)
              </label>
              <template v-if="withRelation">
                <div class="form-group" style="margin-bottom: 8px;">
                  <label>对象关系</label>
                  <select v-model="selectedRelationProp" class="form-select" style="max-width: 400px;">
                    <option value="" disabled>-- 选择关系 --</option>
                    <option v-for="op in objectProperties" :key="op.id" :value="op.id">
                      {{ op.name }} → {{ op.range }}
                    </option>
                  </select>
                  <div v-if="!objectProperties.length" class="hint">该类没有对象关系</div>
                </div>

                <div v-if="selectedRelationProp && relationTargetProperties.length" class="form-group">
                  <label>关联表查询属性 <span class="hint">(不选则查询全部)</span></label>
                  <div class="checkbox-group">
                    <label v-for="tp in relationTargetProperties" :key="tp.name" class="checkbox-item">
                      <input type="checkbox" :checked="relationSelectProps.includes(tp.name)" @change="toggleRelationTargetProperty(tp.name)" />
                      <code>{{ tp.name }}</code>
                      <span class="tag tag-green">{{ tp.data_type || 'string' }}</span>
                    </label>
                  </div>
                </div>
              </template>
            </div>

            <!-- Limit / Offset -->
            <div class="form-row">
              <div class="form-group" style="flex: 1;">
                <label>LIMIT</label>
                <input v-model.number="queryLimit" type="number" class="form-input" min="1" max="10000" />
              </div>
              <div class="form-group" style="flex: 1;">
                <label>OFFSET</label>
                <input v-model.number="queryOffset" type="number" class="form-input" min="0" />
              </div>
            </div>

            <div class="form-actions">
              <button
                class="btn btn-primary"
                :disabled="loading || !selectedClassName"
                @click="executeInstanceQuery"
              >
                {{ loading ? '查询中...' : '▶ 执行查询' }}
              </button>
            </div>
          </template>

          <div v-if="!selectedClassName" class="empty-state" style="padding: 12px 0;">
            <p>请先选择一个目标类</p>
          </div>
        </template>

        <!-- ═══ JSON mode ═══ -->
        <template v-if="instanceQueryMode === 'json'">
          <div class="form-group">
            <label>请求体 (JSON)</label>
            <div class="hint" style="margin-bottom: 6px;">
              格式: <code>SemanticQueryRequest</code> — type, ontology_id, class_name, select, where, relation, limit, offset
            </div>
            <div class="hint" style="margin-bottom: 6px;">
              支持的操作符: <code>= &nbsp; != &nbsp; &gt; &nbsp; &lt; &nbsp; &gt;= &nbsp; &lt;= &nbsp; LIKE &nbsp; NOT LIKE &nbsp; IN</code>
            </div>
            <textarea
              v-model="jsonInput"
              class="form-input json-input"
              rows="12"
              spellcheck="false"
            ></textarea>
            <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
          </div>
          <div class="form-actions" style="display: flex; gap: 8px; flex-wrap: wrap;">
            <button class="btn btn-primary" :disabled="loading || !jsonInput.trim()" @click="executeInstanceQuery">
              {{ loading ? '查询中...' : '▶ 执行查询' }}
            </button>
            <button class="btn btn-secondary" @click="formatJson">格式化 JSON</button>
            <button class="btn btn-secondary" @click="copyJsonInput">{{ jsonCopied ? '✓ 已复制' : '复制' }}</button>
            <button class="btn btn-secondary" @click="jsonInput = buildJsonTemplate()">重置模板</button>
          </div>
        </template>
      </div>

      <!-- Instance results -->
      <div v-if="instanceResult" class="card">
        <div class="card-header" style="display: flex; align-items: center; justify-content: space-between;">
          <div>
            <h3 style="margin: 0;">查询结果</h3>
            <div v-if="instanceResult.relation" class="hint" style="margin-top: 4px;">
              关联: {{ instanceResult.relation.property }} → {{ instanceResult.relation.class }}
              <span v-if="instanceResult.relation.columns"> ({{ instanceResult.relation.columns.join(', ') }})</span>
            </div>
          </div>
          <span class="tag tag-blue">{{ instanceResult.total }} 行</span>
        </div>
        <div v-if="instanceResult.rows.length === 0" class="empty-state" style="padding: 20px;">
          <p>无结果</p>
        </div>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th v-for="col in instanceResult.columns" :key="col" style="font-family: ui-monospace, monospace;">{{ col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in instanceResult.rows" :key="i">
                <td v-for="col in instanceResult.columns" :key="col">
                  <span v-if="row[col] === null" style="color: var(--text-muted);">NULL</span>
                  <span v-else>{{ row[col] }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- ═══════════════ SPARQL QUERY ═══════════════ -->
    <div v-if="activeTab === 'sparql'">
      <QueryEngine :ontology-id="selectedOntologyId" />
    </div>

    <!-- ═══════════════ DSL QUERY ═══════════════ -->
    <div v-if="activeTab === 'dsl'">
      <div class="card" style="margin-bottom: 20px;">
        <div class="form-group">
          <label>DSL 查询 (JSON)</label>
          <div class="hint" style="margin-bottom: 6px;">
            格式: <code>DslQueryRequest</code> — ontology_id, query { subject, projection, filters, traversal, orderBy, pagination, distinct }
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
        <div v-if="dslResult.rows.length === 0" class="empty-state" style="padding: 20px;">
          <p>无结果</p>
        </div>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th v-for="col in dslResult.columns" :key="col" style="font-family: ui-monospace, monospace;">{{ col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in dslResult.rows" :key="i">
                <td v-for="col in dslResult.columns" :key="col">
                  <span v-if="row[col] === null" style="color: var(--text-muted);">NULL</span>
                  <span v-else>{{ row[col] }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
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

/* ── Instance Query ── */
.checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}
.checkbox-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  transition: all 0.1s;
}
.checkbox-item:hover {
  background: var(--bg-muted, #f8fafc);
}
.checkbox-item input[type="checkbox"] {
  accent-color: var(--primary);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.filter-field { flex: 1.5; min-width: 120px; }
.filter-op { flex: 0.8; min-width: 80px; }
.filter-val { flex: 2; }

.json-output {
  margin: 0;
  padding: 16px;
  font-size: 13px;
  line-height: 1.5;
  background: #1e293b;
  color: #e2e8f0;
  overflow-x: auto;
  white-space: pre;
  border-radius: 0 0 var(--radius) var(--radius);
  max-height: 600px;
  overflow-y: auto;
}

/* ── Mode Toggle ── */
.mode-toggle {
  display: flex;
  gap: 0;
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
  width: fit-content;
}
.mode-btn {
  padding: 6px 18px;
  border: none;
  background: var(--bg);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.mode-btn:not(:last-child) {
  border-right: 1px solid var(--border);
}
.mode-btn:hover {
  background: var(--bg-muted, #f8fafc);
  color: var(--text);
}
.mode-btn.active {
  background: var(--primary);
  color: #fff;
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
