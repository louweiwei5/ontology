<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listOntologies, exportOwlOntology, type OntologyListItem } from '../api/ontology'
import { executeDslQuery, getTBoxJson, type SemanticQueryResponse } from '../api/query'

const ontologies = ref<OntologyListItem[]>([])
const selectedOntologyId = ref('')
const error = ref('')
const activeTab = ref<'dsl' | 'tbox' | 'owl-export'>('dsl')

onMounted(async () => {
  try {
    ontologies.value = await listOntologies()
    if (ontologies.value.length > 0) selectedOntologyId.value = ontologies.value[0].id
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
})

const baseUrl = window.location.origin + '/api/services/semantic-query'

// ── DSL Query ──
const dslQuery = ref(`{
  "ontology": {
    "name": "<本体名称>"
  },
  "query": {
    "target": "Orders",
    "select": ["orderNumber", "orderDate", "totalAmount"],
    "filter": {
      "logic": "AND",
      "conditions": [
        {"field": "orderStatus", "operator": "EQ", "value": "completed"}
      ]
    },
    "pagination": {
      "page": 1,
      "size": 10
    }
  }
}`)
const dslOutput = ref<SemanticQueryResponse | null>(null)

// ── TBox ──
const tboxJson = ref<SemanticQueryResponse | null>(null)
const tboxCopied = ref(false)

// ── OWL ──
const owlExportFormat = ref<'rdf-xml' | 'turtle'>('rdf-xml')

const loading = ref(false)

async function tryDslQuery() {
  if (!selectedOntologyId.value) return
  loading.value = true; error.value = ''
  try {
    let body: any
    try {
      body = JSON.parse(dslQuery.value)
    } catch {
      error.value = 'JSON 格式错误，请检查'
      loading.value = false
      return
    }
    // Auto-fill ontology name from selected ontology
    const onto = ontologies.value.find(o => o.id === selectedOntologyId.value)
    if (onto && body.ontology) {
      body.ontology.name = onto.name
    }
    dslOutput.value = await executeDslQuery(body)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally { loading.value = false }
}

async function tryTBox() {
  if (!selectedOntologyId.value) return
  loading.value = true; error.value = ''
  try {
    tboxJson.value = await getTBoxJson(selectedOntologyId.value)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally { loading.value = false }
}

async function copyTBox() {
  if (!tboxJson.value) return
  navigator.clipboard.writeText(JSON.stringify(tboxJson.value, null, 2))
  tboxCopied.value = true; setTimeout(() => tboxCopied.value = false, 2000)
}

async function downloadOwl(format: 'rdf-xml' | 'turtle') {
  if (!selectedOntologyId.value) { error.value = '请先选择本体'; return }
  const ontoName = ontologies.value.find(o => o.id === selectedOntologyId.value)?.name || 'ontology'
  try {
    const blob = await exportOwlOntology(selectedOntologyId.value, format) as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${ontoName}${format === 'turtle' ? '.ttl' : '.owl'}`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h2>对外服务接口</h2>
      <p>Semantic Query Service — JSON 格式输入输出，便于大模型集成</p>
    </div>

    <!-- Ontology selector -->
    <div class="card" style="margin-bottom: 20px;">
      <div class="form-group">
        <label>选择本体（用于在线测试）</label>
        <select v-model="selectedOntologyId" class="form-select" style="max-width: 400px;">
          <option v-for="o in ontologies" :key="o.id" :value="o.id">{{ o.name }}</option>
        </select>
      </div>
      <div class="base-url-line">
        <strong>Base URL:</strong>
        <code>{{ baseUrl }}</code>
      </div>
    </div>

    <!-- Tab bar -->
    <div class="tab-bar">
      <button :class="['tab-btn', { active: activeTab === 'dsl' }]" @click="activeTab = 'dsl'">DSL 查询</button>
      <button :class="['tab-btn', { active: activeTab === 'tbox' }]" @click="activeTab = 'tbox'">本体 TBOX</button>
      <button :class="['tab-btn', { active: activeTab === 'owl-export' }]" @click="activeTab = 'owl-export'">OWL 导出</button>
    </div>

    <!-- ═══════════════ DSL QUERY ═══════════════ -->
    <div v-if="activeTab === 'dsl'" class="tab-content">
      <!-- Interface doc -->
      <div class="card">
        <div class="card-header">
          <span class="method post">POST</span>
          <code class="ep">/dsl/query</code>
        </div>
        <div class="api-section">
          <h4 class="sec-title">接口说明</h4>
          <p class="sec-body">DSL 语义查询引擎，基于本体模型将 JSON DSL 转换为 SQL 执行。支持投影、过滤、关联查询（JOIN）、分页、聚合函数。</p>

          <h4 class="sec-title">请求体结构（完整模板）</h4>
          <pre class="code-block">{
  "ontology": {                        // 本体标识（必填）
    "name": "string",                  //   本体名称（必填）
    "namespace": "string",             //   命名空间（选填）
    "version": "string"                //   版本号（选填）
  },
  "query": {
    "target": "string",                // 查询目标类名（必填）
    "select": [                        // 选择字段（选填，默认返回全部字段）
      "fieldName",                     //   直接字段名
      {                                //   或对象 —— 关联查询
        "relation": "relationName",    //     对象属性名
        "nested_fields": [             //     展开的嵌套字段
          "field1",
          { "relation": "subRel", "nested_fields": ["subField1"] }
        ]
      }
    ],
    "filter": {                        // 过滤条件（选填）
      "logic": "AND",                  //   逻辑：AND | OR
      "conditions": [
        {                              //   简单条件：
          "field": "fieldName",        //     字段名
          "operator": "EQ",            //     操作符
          "value": "value"             //     值
        },
        {                              //   或嵌套条件组：
          "logic": "OR",
          "conditions": [ ... ]
        }
      ]
    },
    "pagination": {                    // 分页（选填）
      "page": 1,                       //   页码，从 1 开始
      "size": 10                       //   每页条数
    }
  }
}</pre>

          <h4 class="sec-title">支持的操作符 (operator)</h4>
          <pre class="code-block">EQ    NEQ    GT    GTE    LT    LTE    IN    NOT_IN
BETWEEN    LIKE    CONTAINS    STARTS_WITH    ENDS_WITH
IS_NULL    IS_NOT_NULL</pre>

          <h4 class="sec-title">关联查询（relation）</h4>
          <p class="sec-body">在 select 中使用对象（relation + nested_fields）可以沿着对象属性跨表关联查询，例如查 Orders 时同时展示关联的 Customer 名称。支持多级嵌套（如 Orders → Customer → Region）。</p>

          <h4 class="sec-title">请求示例</h4>
          <pre class="code-block">curl -X POST {{ baseUrl }}/dsl/query \
  -H "Content-Type: application/json" \
  -d '{
    "ontology": { "name": "CustomerService" },
    "query": {
      "target": "Orders",
      "select": [
        "orderNumber",
        "orderDate",
        "totalAmount",
        { "relation": "customer", "nested_fields": ["customerName", "contactPhone"] }
      ],
      "filter": {
        "logic": "AND",
        "conditions": [
          { "field": "orderStatus", "operator": "EQ", "value": "completed" },
          { "field": "totalAmount", "operator": "GT", "value": 1000 }
        ]
      },
      "pagination": { "page": 1, "size": 10 }
    }
  }'</pre>

          <h4 class="sec-title">响应结构</h4>
          <pre class="code-block">{
  "columns": ["字段1", "字段2", ...],         // 列名列表（属性名）
  "rows": [                                    // 数据行（平铺模式）
    { "字段1": "值", "字段2": "值", ... }
  ],
  "data": [                                    // 嵌套数据（关联查询时存在）
    { "字段1": "值", ..., "relationName": [
        { "子字段1": "值", ... }
      ]
    }
  ],
  "total": 100,                                // 总记录数
  "message": "Query executed successfully",     // 执行信息
  "sql": "SELECT ..."                          // 生成的 SQL（用于诊断）
}</pre>
        </div>
      </div>

      <!-- Online test -->
      <div class="card">
        <div class="card-header"><h4 style="margin:0;">在线测试</h4></div>
        <div class="api-section">
          <div class="form-group">
            <label>请求体 (JSON DSL)</label>
            <textarea v-model="dslQuery" class="form-input code-input" rows="10"></textarea>
          </div>
          <div class="hint" style="margin-bottom: 12px;">提示：选择上方本体后发送请求，系统会自动填入本体名称。DSL 中的 target 需为本体中的类名。</div>
          <button class="btn btn-primary" :disabled="loading" @click="tryDslQuery">
            {{ loading ? '请求中...' : '发送请求' }}
          </button>

          <div v-if="dslOutput" class="test-result">
            <div class="result-label">
              响应结果
              <span v-if="dslOutput.total != null" style="font-weight:400;font-size:12px;color:var(--text-muted);">
                — 共 {{ dslOutput.total }} 条记录
              </span>
            </div>
            <div v-if="dslOutput.message" class="result-label" style="font-weight:400;font-size:12px;color:var(--text-muted);">
              {{ dslOutput.message }}
            </div>
            <div v-if="dslOutput.sql" class="result-label" style="font-weight:400;font-size:12px;">
              <details>
                <summary style="cursor:pointer;color:var(--text-muted);font-size:12px;">生成的 SQL</summary>
                <pre style="margin-top:6px;padding:10px;background:#1e293b;color:#e2e8f0;border-radius:4px;font-size:12px;overflow-x:auto;">{{ dslOutput.sql }}</pre>
              </details>
            </div>

            <!-- Flat result table (rows) -->
            <div v-if="dslOutput.rows && dslOutput.rows.length > 0" class="table-wrap">
              <table>
                <thead><tr><th v-for="col in dslOutput.columns" :key="col">{{ col }}</th></tr></thead>
                <tbody>
                  <tr v-for="(row, i) in dslOutput.rows" :key="i">
                    <td v-for="col in dslOutput.columns" :key="col">{{ row[col] ?? 'NULL' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Nested result (data) -->
            <div v-else-if="dslOutput.data && dslOutput.data.length > 0">
              <div v-for="(item, i) in dslOutput.data" :key="i" class="nested-row">
                <pre class="pre-wrap">{{ JSON.stringify(item, null, 2) }}</pre>
              </div>
            </div>

            <div v-else style="padding:16px;text-align:center;color:var(--text-muted);">无结果</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══════════════ TBOX ═══════════════ -->
    <div v-if="activeTab === 'tbox'" class="tab-content">
      <div class="card">
        <div class="card-header">
          <span class="method get">GET</span>
          <code class="ep">/tbox/{ontology_id}?format=json|markdown|manchester</code>
        </div>
        <div class="api-section">
          <h4 class="sec-title">接口说明</h4>
          <p class="sec-body">获取本体的 TBox 模式信息，支持 JSON、Markdown 和 Manchester OWL Syntax 三种格式。JSON 格式适合大模型程序化处理。</p>

          <h4 class="sec-title">查询参数</h4>
          <pre class="code-block">format : string — 输出格式（选填，默认 markdown）
  - json       : 结构化 JSON（推荐，适合 LLM 交互）
  - markdown   : 结构化 Markdown（适合 LLM 上下文）
  - manchester : Manchester OWL Syntax（标准格式）

路径参数: ontology_id : string  — 本体 ID</pre>

          <h4 class="sec-title">请求示例</h4>
          <pre class="code-block"># JSON 格式（推荐）
curl {{ baseUrl }}/tbox/xxx?format=json

# Markdown 格式
curl {{ baseUrl }}/tbox/xxx?format=markdown</pre>

          <h4 class="sec-title">JSON 响应结构</h4>
          <pre class="code-block">{
  "ontology": {
    "name": "CustomerService",
    "namespace": "http://example.com/cs#",
    "version": "1.0.0",
    "description": "..."
  },
  "classes": [
    {
      "name": "Customers",
      "description": "客户信息",
      "parent": null,
      "data_properties": [
        {"name": "customerName", "data_type": "string", "description": "客户姓名"}
      ]
    }
  ],
  "relationships": [
    {"name": "place", "relation_type": "one-to-many",
     "source_class": "Customers", "target_class": "Orders",
     "mapping_rules": [{"domain_property":"customerId","range_property":"customerId"}]}
  ]
}</pre>
        </div>
      </div>

      <!-- Online test -->
      <div class="card">
        <div class="card-header"><h4 style="margin:0;">在线测试</h4></div>
        <div class="api-section">
          <button class="btn btn-primary" :disabled="loading" @click="tryTBox">
            {{ loading ? '请求中...' : '获取 TBox (JSON)' }}
          </button>

          <div v-if="tboxJson" class="test-result">
            <div class="result-label">
              本体结构：{{ tboxJson.ontology?.name }}
              <div class="result-actions">
                <button class="btn btn-sm" @click="copyTBox">{{ tboxCopied ? '✓ 已复制' : '复制' }}</button>
              </div>
            </div>
            <pre class="tbox-content">{{ JSON.stringify(tboxJson, null, 2) }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══════════════ OWL 导出 ═══════════════ -->
    <div v-if="activeTab === 'owl-export'" class="tab-content">
      <div class="card">
        <div class="card-header">
          <span class="method get">GET</span>
          <code class="ep">/export/{ontology_id}/owl?format=rdf-xml|turtle</code>
        </div>
        <div class="api-section">
          <h4 class="sec-title">接口说明</h4>
          <p class="sec-body">将本体导出为标准 OWL/RDF 文件，兼容 Protégé 等标准本体编辑工具。支持 RDF/XML 和 Turtle 两种序列化格式。</p>

          <h4 class="sec-title">查询参数</h4>
          <pre class="code-block">format : string — 序列化格式（选填，默认 rdf-xml）
  - rdf-xml : RDF/XML (.owl)
  - turtle  : Turtle (.ttl)</pre>

          <h4 class="sec-title">请求示例</h4>
          <pre class="code-block"># RDF/XML（默认）
curl {{ baseUrl }}/export/ba517df8-.../owl

# Turtle
curl {{ baseUrl }}/export/ba517df8-.../owl?format=turtle</pre>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><h4 style="margin:0;">在线下载</h4></div>
        <div class="api-section">
          <div style="display: flex; gap: 12px; align-items: flex-end;">
            <div class="form-group" style="flex: 0;">
              <label>序列化格式</label>
              <select v-model="owlExportFormat" class="form-select">
                <option value="rdf-xml">RDF/XML (.owl)</option>
                <option value="turtle">Turtle (.ttl)</option>
              </select>
            </div>
            <button class="btn btn-primary" :disabled="!selectedOntologyId" @click="downloadOwl(owlExportFormat)">
              下载 OWL 文件
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="error" class="alert alert-error" style="margin-top:20px;">{{ error }}</div>
  </div>
</template>

<style scoped>
.base-url-line {
  margin-top: 8px;
  font-size: 13px;
}
.base-url-line code {
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
}
.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--border);
  margin-bottom: 20px;
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
.tab-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.api-section {
  padding: 12px 0;
}
.sec-title {
  font-size: 14px;
  font-weight: 600;
  margin: 16px 0 8px;
  color: var(--text);
}
.sec-title:first-child { margin-top: 0; }
.sec-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text);
  margin: 0;
}
.method {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: #fff;
}
.method.get { background: #22c55e; }
.method.post { background: #3b82f6; }
.ep {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-left: 8px;
}
.code-block {
  margin: 0;
  padding: 14px;
  font-size: 13px;
  line-height: 1.5;
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 6px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.code-input {
  font-family: ui-monospace, monospace;
  font-size: 14px;
  min-height: 70px;
  resize: vertical;
}
.test-result {
  margin-top: 16px;
}
.result-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
}
.tbox-content {
  margin: 0;
  padding: 14px;
  font-size: 12px;
  line-height: 1.6;
  background: #f8fafc;
  border-radius: 6px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 500px;
  overflow-y: auto;
}
.btn-sm { font-size: 12px; padding: 4px 12px; }
.result-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.pre-wrap {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
  background: #f8fafc;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 4px 0;
}
.nested-row + .nested-row {
  margin-top: 8px;
}
</style>
