<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listOntologies, exportOwlOntology, type OntologyListItem } from '../api/ontology'
import { executeSemanticQuery, getTBoxJson, type SemanticQueryResponse } from '../api/query'

const ontologies = ref<OntologyListItem[]>([])
const selectedOntologyId = ref('')
const error = ref('')
const activeTab = ref<'instance' | 'tbox' | 'owl-export'>('instance')

onMounted(async () => {
  try {
    ontologies.value = await listOntologies()
    if (ontologies.value.length > 0) selectedOntologyId.value = ontologies.value[0].id
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
})

const baseUrl = window.location.origin + '/api/services/semantic-query'

// ── Instance Query ──
const instanceQuery = ref(`{
  "type": "instance",
  "ontology_id": "<本体ID>",
  "class_name": "Customers",
  "select": ["customerName", "contactPhone"],
  "where": [
    {"field": "customerStatus", "op": "=", "value": "active"}
  ],
  "relation": {
    "property": "place",
    "select": ["orderNo", "amount"]
  },
  "limit": 10
}`)
const instanceOutput = ref<SemanticQueryResponse | null>(null)

// ── TBox ──
const tboxJson = ref<SemanticQueryResponse | null>(null)
const tboxCopied = ref(false)

// ── OWL ──
const owlExportFormat = ref<'rdf-xml' | 'turtle'>('rdf-xml')

const loading = ref(false)

async function tryInstanceQuery() {
  if (!selectedOntologyId.value) return
  loading.value = true; error.value = ''
  try {
    let body: any
    try {
      body = JSON.parse(instanceQuery.value)
    } catch {
      error.value = 'JSON 格式错误，请检查'
      loading.value = false
      return
    }
    body.ontology_id = selectedOntologyId.value
    instanceOutput.value = await executeSemanticQuery(body)
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
      <button :class="['tab-btn', { active: activeTab === 'instance' }]" @click="activeTab = 'instance'">实例查询</button>
      <button :class="['tab-btn', { active: activeTab === 'tbox' }]" @click="activeTab = 'tbox'">本体 TBOX</button>
      <button :class="['tab-btn', { active: activeTab === 'owl-export' }]" @click="activeTab = 'owl-export'">OWL 导出</button>
    </div>

    <!-- ═══════════════ INSTANCE QUERY ═══════════════ -->
    <div v-if="activeTab === 'instance'" class="tab-content">
      <!-- Interface doc -->
      <div class="card">
        <div class="card-header">
          <span class="method post">POST</span>
          <code class="ep">/query</code>
        </div>
        <div class="api-section">
          <h4 class="sec-title">接口说明</h4>
          <p class="sec-body">统一的语义查询接口。使用 JSON 格式输入输出，支持实例数据查询和可选的关联查询（替代旧版 DSL + 实体关系查询）。</p>

          <h4 class="sec-title">请求体结构</h4>
          <pre class="code-block">{
  "type": "instance",                              // 查询类型
  "ontology_id": "string",                         // 本体 ID
  "class_name": "string",                          // 查询的类名
  "select": ["prop1", "prop2"],                    // 选查的属性（选填）
  "where": [                                       // 过滤条件（选填）
    {"field": "prop", "op": "=", "value": "val"}
  ],
  "relation": {                                    // 关联查询（选填）
    "property": "relationProp",                    // 对象属性名
    "select": ["targetProp1"]                      // 目标类属性（选填）
  },
  "limit": 100,                                    // 限制行数（选填）
  "offset": 0                                      // 偏移量（选填）
}</pre>

          <h4 class="sec-title">支持的操作符 (op)</h4>
          <pre class="code-block">=   !=   >   <   >=   <=   LIKE   NOT LIKE   IN</pre>

          <h4 class="sec-title">请求示例</h4>
          <pre class="code-block">curl -X POST {{ baseUrl }}/query \
  -H "Content-Type: application/json" \
  -d '{
    "type": "instance",
    "ontology_id": "ba517df8-...",
    "class_name": "Customers",
    "select": ["customerName", "contactPhone"],
    "where": [{"field": "customerStatus", "op": "=", "value": "active"}],
    "relation": {"property": "place", "select": ["orderNo", "amount"]},
    "limit": 10
  }'</pre>

          <h4 class="sec-title">响应结构</h4>
          <pre class="code-block">{
  "type": "instance",
  "class_name": "Customers",
  "relation": {
    "property": "place",
    "class": "Orders",
    "columns": ["orderNo", "amount"]
  },
  "columns": ["customerName", "contactPhone", "orderNo", "amount"],
  "rows": [
    {"customerName": "张三", "contactPhone": "138...", "orderNo": "ORD001", "amount": 299.0}
  ],
  "total": 1
}</pre>
        </div>
      </div>

      <!-- Online test -->
      <div class="card">
        <div class="card-header"><h4 style="margin:0;">在线测试</h4></div>
        <div class="api-section">
          <div class="form-group">
            <label>请求体 (JSON)</label>
            <textarea v-model="instanceQuery" class="form-input code-input" rows="8"></textarea>
          </div>
          <div class="hint" style="margin-bottom: 12px;">提示：替换 ontology_id 为实际值，修改 class_name 和字段名后测试</div>
          <button class="btn btn-primary" :disabled="loading" @click="tryInstanceQuery">
            {{ loading ? '请求中...' : '发送请求' }}
          </button>

          <div v-if="instanceOutput" class="test-result">
            <div class="result-label">响应结果（共 {{ instanceOutput.total }} 行）</div>
            <div v-if="instanceOutput.relation" class="result-label" style="font-weight:400;font-size:12px;">
              关联: {{ instanceOutput.relation.property }} → {{ instanceOutput.relation.class }}
            </div>
            <div class="table-wrap">
              <table>
                <thead><tr><th v-for="col in instanceOutput.columns" :key="col">{{ col }}</th></tr></thead>
                <tbody>
                  <tr v-for="(row, i) in instanceOutput.rows" :key="i">
                    <td v-for="col in instanceOutput.columns" :key="col">{{ row[col] ?? 'NULL' }}</td>
                  </tr>
                  <tr v-if="instanceOutput.rows.length === 0">
                    <td :colspan="instanceOutput.columns.length" style="text-align:center;color:var(--text-muted);">无结果</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- Legacy DSL info -->
      <div class="card" style="border-left: 3px solid var(--text-muted);">
        <div class="api-section">
          <h4 class="sec-title" style="color: var(--text-muted);">旧版接口（已废弃）</h4>
          <p class="sec-body" style="font-size:13px;">
            <code>POST /dsl-query</code> 和 <code>POST /entity-relations</code> 已合并到 <code>POST /query</code> 中。
            旧接口保留兼容，但建议统一使用 <code>/query</code>。
          </p>
        </div>
      </div>

      <!-- DSL Query -->
      <div class="card">
        <div class="card-header">
          <span class="method post">POST</span>
          <code class="ep">/api/dsl/query</code>
        </div>
        <div class="api-section">
          <h4 class="sec-title">接口说明</h4>
          <p class="sec-body">DSL 查询引擎。支持投影(projection)、过滤(filters)、关联查询(traversal/JOIN)、排序(orderBy)、分页(pagination)、去重(distinct)和聚合函数。</p>

          <h4 class="sec-title">请求体结构</h4>
          <pre class="code-block">{
  "ontology_id": "string, 本体ID",
  "query": {
    "subject": {
      "entity": "string, 主查询实体名（必填）",
      "alias": "string, 别名（可选）"
    },
    "projection": [
      {
        "entity": "string, 实体名（默认subject）",
        "property": "string, 属性名（必填）",
        "alias": "string, 列别名",
        "aggregation": "COUNT|SUM|AVG|MIN|MAX|COUNT_DISTINCT"
      }
    ],
    "filters": {
      "logic": "AND|OR",
      "conditions": [
        {
          "entity": "string, 实体名",
          "property": "string, 属性名",
          "operator": "EQ|NEQ|GT|GTE|LT|LTE|IN|NOT_IN|BETWEEN|CONTAINS|STARTS_WITH|ENDS_WITH|IS_NULL|IS_NOT_NULL",
          "value": "any",
          "valueType": "STRING|NUMBER|DATE|BOOLEAN"
        }
      ],
      "groups": [{ "logic": "AND|OR", "conditions": [...] }]
    },
    "traversal": [
      {
        "from": "string, 起始实体",
        "to": "string, 目标实体",
        "relation": "string, 对象属性名",
        "direction": "OUT|IN|BOTH",
        "optional": false
      }
    ],
    "orderBy": [{ "property": "string", "direction": "ASC|DESC" }],
    "pagination": { "page": 1, "pageSize": 10 },
    "distinct": false
  }
}</pre>

          <h4 class="sec-title">请求示例</h4>
          <pre class="code-block">curl -X POST http://localhost:8081/api/dsl/query \
  -H "Content-Type: application/json" \
  -d '{
    "ontology_id": "本体ID",
    "query": {
      "subject": { "entity": "Product" },
      "projection": [
        { "property": "name" },
        { "entity": "ProductCategory", "property": "name", "alias": "category" }
      ],
      "traversal": [
        { "from": "Product", "to": "ProductCategory",
          "relation": "belongTo", "direction": "OUT" }
      ],
      "pagination": { "limit": 5 },
      "orderBy": [{ "property": "name", "direction": "ASC" }]
    }
  }'</pre>
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
</style>
