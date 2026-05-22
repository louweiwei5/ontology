<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getDbConnection, listTables, createTableImport, applyTableImport,
  type DbConnectionResponse, type TableInfo,
} from '../../api/dbConnection'
import { listOntologies, type OntologyListItem } from '../../api/ontology'

const route = useRoute()
const router = useRouter()
const connId = route.params.connId as string

const conn = ref<DbConnectionResponse | null>(null)
const tables = ref<TableInfo[]>([])
const ontologies = ref<OntologyListItem[]>([])
const loading = ref(true)
const error = ref('')
const selectedTables = ref<Set<string>>(new Set())
const selectedOntologyId = ref('')
const importing = ref(false)
const resultMsg = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [c, tbl, onts] = await Promise.all([
      getDbConnection(connId),
      listTables(connId),
      listOntologies(),
    ])
    conn.value = c
    tables.value = tbl
    ontologies.value = onts
    if (onts.length > 0) selectedOntologyId.value = onts[0].id
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

function toggleTable(name: string) {
  if (selectedTables.value.has(name)) {
    selectedTables.value.delete(name)
  } else {
    selectedTables.value.add(name)
  }
  // Force reactivity
  selectedTables.value = new Set(selectedTables.value)
}

function selectAll() {
  selectedTables.value = new Set(tables.value.map(t => t.table_name))
}

function deselectAll() {
  selectedTables.value = new Set()
}

async function handleImport() {
  if (selectedTables.value.size === 0) {
    alert('请至少选择一个表')
    return
  }
  if (!selectedOntologyId.value) {
    alert('请选择目标本体')
    return
  }

  importing.value = true
  resultMsg.value = ''
  try {
    const ti = await createTableImport(connId, {
      ontology_id: selectedOntologyId.value,
      tables: Array.from(selectedTables.value),
    })

    // Auto-apply the mapping
    const result = await applyTableImport(ti.id)
    resultMsg.value = `成功导入 ${selectedTables.value.size} 个表到本体 "${ontologies.value.find(o => o.id === selectedOntologyId.value)?.name}"`
    selectedTables.value = new Set()

    // Refresh table list to show only unimported
    tables.value = await listTables(connId)
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  } finally {
    importing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header" style="display: flex; align-items: flex-start; justify-content: space-between;">
      <div>
        <h2>导入表结构</h2>
        <p v-if="conn">数据库：{{ conn.name }} ({{ conn.host }}:{{ conn.port }}/{{ conn.database_name }})</p>
      </div>
      <router-link to="/db-connections" class="btn btn-secondary">← 返回连接列表</router-link>
    </div>

    <div v-if="error" class="alert alert-error">{{ error }}</div>
    <div v-if="resultMsg" class="alert alert-success">{{ resultMsg }}</div>

    <div v-if="loading" class="loading">加载中...</div>
    <template v-else>
      <!-- Step 1: Select ontology -->
      <div class="card" style="margin-bottom: 20px;">
        <div class="card-header"><h3>目标本体</h3></div>
        <div class="form-group" style="margin-bottom: 0;">
          <select v-model="selectedOntologyId" class="form-select" style="max-width: 400px;">
            <option v-for="o in ontologies" :key="o.id" :value="o.id">{{ o.name }}</option>
          </select>
          <div class="hint">选择要将表结构导入到的目标本体</div>
        </div>
      </div>

      <!-- Step 2: Select tables -->
      <div class="card" style="margin-bottom: 20px;">
        <div class="card-header">
          <h3>选择表 <span v-if="tables.length > 0" class="tag tag-orange">{{ selectedTables.size }} / {{ tables.length }}</span></h3>
          <div style="display: flex; gap: 6px;">
            <button class="btn btn-secondary btn-sm" @click="selectAll">全选</button>
            <button class="btn btn-secondary btn-sm" @click="deselectAll">取消</button>
          </div>
        </div>

        <div v-if="tables.length === 0" class="empty-state" style="padding: 20px;">
          <p>该数据库中暂无表</p>
        </div>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th style="width: 40px;"></th>
                <th>表名</th>
                <th>注释</th>
                <th>映射类名</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tbl in tables" :key="tbl.table_name"
                :class="{ 'selected-row': selectedTables.has(tbl.table_name) }"
                @click="toggleTable(tbl.table_name)" style="cursor: pointer;">
                <td>
                  <input type="checkbox" :checked="selectedTables.has(tbl.table_name)" @click.stop="toggleTable(tbl.table_name)" />
                </td>
                <td style="font-weight: 500; font-family: ui-monospace, monospace;">{{ tbl.table_name }}</td>
                <td style="color: var(--text-muted);">{{ tbl.table_comment || '—' }}</td>
                <td><span class="tag tag-blue">{{ tbl.table_name.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).replace(/\s+/g, '') }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Import button -->
      <div class="form-actions">
        <button class="btn btn-primary" :disabled="importing || selectedTables.size === 0 || !selectedOntologyId" @click="handleImport">
          {{ importing ? '导入中...' : `导入 ${selectedTables.size} 个表` }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
tr.selected-row td { background: #eef2ff; }
tr.selected-row:hover td { background: #e0e7ff; }
</style>
