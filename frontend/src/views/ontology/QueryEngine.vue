<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { executeSparql, type QueryResponse } from '../../api/query'
import { getOntology, type OntologyResponse } from '../../api/ontology'

const props = withDefaults(defineProps<{ ontologyId?: string }>(), { ontologyId: '' })
const route = useRoute()
const ontologyId = computed(() => props.ontologyId || (route.params.id as string))

const ontology = ref<OntologyResponse | null>(null)
const sparql = ref('SELECT ?c ?n WHERE { ?c a :ProductCategory ; :name ?n }')
const loading = ref(false)
const error = ref('')
const result = ref<QueryResponse | null>(null)
const resultFormat = ref<'table' | 'json'>('table')
const history = ref<string[]>([])

async function load() {
  try {
    ontology.value = await getOntology(ontologyId.value)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
}

async function handleQuery() {
  if (!sparql.value.trim()) return
  loading.value = true
  error.value = ''
  result.value = null
  try {
    result.value = await executeSparql(ontologyId.value, sparql.value.trim())
    // Add to history (deduped, most recent first)
    history.value = [sparql.value.trim(), ...history.value.filter(h => h !== sparql.value.trim())].slice(0, 20)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

function fillFromHistory(q: string) {
  sparql.value = q
}

function insertExample(example: string) {
  sparql.value = example
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header" style="display: flex; align-items: flex-start; justify-content: space-between;">
      <div>
        <h2>SPARQL 查询引擎</h2>
        <p v-if="ontology">{{ ontology.name }} — 使用标准 SPARQL 查询本体图（含实例数据）</p>
      </div>
      <router-link :to="`/ontologies/${ontologyId}`" class="btn btn-secondary">← 返回本体</router-link>
    </div>

    <!-- SPARQL Input -->
    <div class="card" style="margin-bottom: 20px;">
      <div class="card-header"><h3>SPARQL 查询</h3></div>
      <div class="form-group">
        <textarea
          v-model="sparql"
          class="form-input query-input"
          placeholder='SELECT ?p ?n WHERE { ?p a :Product ; :name ?n } LIMIT 10'
          @keydown.ctrl.enter="handleQuery"
        ></textarea>
        <div class="hint">按 Ctrl+Enter 执行 · 使用 <code>:ClassName</code> 引用本体类，<code>rdfs:label</code> / <code>rdf:type</code> 为标准 RDF 属性</div>
      </div>
      <div class="form-actions">
        <button class="btn btn-primary" :disabled="loading || !sparql.trim()" @click="handleQuery">
          {{ loading ? '执行中...' : '▶ 执行查询' }}
        </button>
        <button class="btn btn-secondary" @click="sparql = ''">清空</button>
      </div>
    </div>

    <!-- Example Queries -->
    <div class="card" style="margin-bottom: 20px;">
      <div class="card-header"><h3>示例查询</h3></div>
      <div style="display: flex; flex-wrap: wrap; gap: 6px;">
        <button
          class="tag tag-blue"
          style="cursor: pointer; font-family: ui-monospace, monospace; font-size: 12px; padding: 4px 8px;"
          @click="insertExample('SELECT ?c ?n ?l WHERE { ?c a :ProductCategory ; :name ?n ; :label ?l }')"
        >所有产品类别</button>
        <button
          class="tag tag-blue"
          style="cursor: pointer; font-family: ui-monospace, monospace; font-size: 12px; padding: 4px 8px;"
          @click="insertExample('SELECT ?p ?n ?cat WHERE { ?p a :Product ; :name ?n ; :belongsToCategory ?cat }')"
        >产品及其所属类别</button>
        <button
          class="tag tag-blue"
          style="cursor: pointer; font-family: ui-monospace, monospace; font-size: 12px; padding: 4px 8px;"
          @click="insertExample('SELECT ?caName ?attrName ?attrLabel WHERE { ?ca a :CategoryAttribute ; :definesAttribute ?ad . ?ad :attrName ?attrName ; :label ?attrLabel } ORDER BY ?attrName')"
        >所有分类属性定义</button>
        <button
          class="tag tag-blue"
          style="cursor: pointer; font-family: ui-monospace, monospace; font-size: 12px; padding: 4px 8px;"
          @click="insertExample('SELECT ?class ?label WHERE { ?class rdf:type owl:Class ; rdfs:label ?label }')"
        >所有类（TBox）</button>
        <button
          class="tag tag-blue"
          style="cursor: pointer; font-family: ui-monospace, monospace; font-size: 12px; padding: 4px 8px;"
          @click="insertExample('SELECT ?product ?name ?value WHERE { ?av a :AttributeValue ; :ofProduct ?product ; :valueString ?value . ?product :name ?name } LIMIT 20')"
        >产品属性值</button>
      </div>
    </div>

    <!-- Query History -->
    <div v-if="history.length > 0" class="card" style="margin-bottom: 20px;">
      <div class="card-header"><h3>查询历史</h3></div>
      <div style="display: flex; flex-wrap: wrap; gap: 6px;">
        <button
          v-for="(q, i) in history" :key="i"
          class="tag tag-orange"
          style="cursor: pointer; font-family: ui-monospace, monospace; font-size: 12px; padding: 4px 8px;"
          @click="fillFromHistory(q)"
        >{{ q }}</button>
      </div>
    </div>

    <!-- Error -->
    <div v-if="error" class="alert alert-error" style="margin-bottom: 20px;">{{ error }}</div>

    <!-- Results -->
    <div v-if="result" class="card">
      <div class="card-header" style="display: flex; align-items: center; justify-content: space-between;">
        <h3 style="margin: 0;">查询结果 <span class="tag tag-orange">{{ result.total }} {{ result.format === 'nested' ? '个对象' : '行' }}</span></h3>
        <div style="display: flex; gap: 4px;">
          <button :class="['format-pill', { active: resultFormat === 'table' }]" @click="resultFormat = 'table'">表格</button>
          <button :class="['format-pill', { active: resultFormat === 'json' }]" @click="resultFormat = 'json'">JSON</button>
        </div>
      </div>

      <div v-if="result.rows.length === 0" class="empty-state" style="padding: 20px;">
        <p>查询无结果</p>
      </div>
      <div v-else-if="resultFormat === 'table'" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th v-for="col in result.columns" :key="col" style="font-family: ui-monospace, monospace;">{{ col }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in result.rows" :key="i">
              <td v-for="col in result.columns" :key="col">
                <span v-if="row[col] === null" style="color: var(--text-muted);">NULL</span>
                <span v-else>{{ row[col] }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <pre v-else class="json-output">{{ JSON.stringify(result.format === 'nested' ? result.data : result.rows, null, 2) }}</pre>
    </div>
  </div>
</template>

<style scoped>
.query-input {
  min-height: 100px;
  font-family: ui-monospace, monospace;
  font-size: 14px;
  line-height: 1.5;
  resize: vertical;
}
.tag {
  user-select: none;
}
.format-pill {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--text-muted);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}
.format-pill.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}
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
</style>
