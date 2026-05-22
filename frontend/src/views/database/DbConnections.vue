<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  listDbConnections, createDbConnection, updateDbConnection,
  deleteDbConnection, testDbConnection,
  type DbConnectionResponse,
} from '../../api/dbConnection'

const connections = ref<DbConnectionResponse[]>([])
const loading = ref(true)
const error = ref('')
const showForm = ref(false)
const editing = ref(false)
const form = ref({ name: '', description: '', host: 'localhost', port: 3306, database_name: '', username: 'root', password: '' })
const saving = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)
const testing = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    connections.value = await listDbConnections()
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  form.value = { name: '', description: '', host: 'localhost', port: 3306, database_name: '', username: 'root', password: '' }
  testResult.value = null
  showForm.value = true
}

function openEdit(conn: DbConnectionResponse) {
  editing.value = true
  form.value = { name: conn.name, description: conn.description || '', host: conn.host, port: conn.port, database_name: conn.database_name, username: conn.username, password: '' }
  testResult.value = null
  showForm.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      const data: any = { ...form.value }
      if (!data.password) delete data.password
      await updateDbConnection(connections.value.find(c => c.host === form.value.host)?.id || '', data)
    } else {
      await createDbConnection(form.value)
    }
    showForm.value = false
    await load()
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string, name: string) {
  if (!confirm(`确认删除数据库连接 "${name}"？`)) return
  try {
    await deleteDbConnection(id)
    await load()
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

async function handleTest(id: string) {
  testing.value = id
  testResult.value = null
  try {
    const result = await testDbConnection(id)
    testResult.value = result
  } catch (e: any) {
    testResult.value = { success: false, message: e.response?.data?.detail || e.message }
  } finally {
    testing.value = null
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header" style="display: flex; align-items: flex-start; justify-content: space-between;">
      <div>
        <h2>数据库连接</h2>
        <p>管理 MySQL 数据库连接，用于导入表结构到本体</p>
      </div>
      <button v-if="!showForm" class="btn btn-primary" @click="openCreate">➕ 新建连接</button>
    </div>

    <div v-if="error" class="alert alert-error">{{ error }}</div>

    <!-- Create/Edit Form -->
    <div v-if="showForm" class="card" style="margin-bottom: 20px; max-width: 600px;">
      <div class="card-header">
        <h3>{{ editing ? '编辑连接' : '新建连接' }}</h3>
      </div>
      <form @submit.prevent="save">
        <div class="form-group">
          <label>名称</label>
          <input v-model="form.name" class="form-input" required placeholder="例如：开发环境 MySQL" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>主机</label>
            <input v-model="form.host" class="form-input" required placeholder="localhost" />
          </div>
          <div class="form-group" style="flex: 0.5;">
            <label>端口</label>
            <input v-model.number="form.port" class="form-input" type="number" required />
          </div>
        </div>
        <div class="form-group">
          <label>数据库名</label>
          <input v-model="form.database_name" class="form-input" required placeholder="my_database" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>用户名</label>
            <input v-model="form.username" class="form-input" required placeholder="root" />
          </div>
          <div class="form-group">
            <label>密码 <span v-if="editing" style="font-weight: normal; color: var(--text-muted);">(留空不变)</span></label>
            <input v-model="form.password" class="form-input" type="password" :required="!editing" />
          </div>
        </div>
        <div class="form-group">
          <label>描述（可选）</label>
          <input v-model="form.description" class="form-input" placeholder="连接用途说明" />
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
          <button type="button" class="btn btn-secondary" @click="showForm = false">取消</button>
        </div>
      </form>
    </div>

    <!-- Connection list -->
    <div class="card">
      <div class="card-header"><h3>已配置的连接</h3></div>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="connections.length === 0" class="empty-state">
        <div class="icon">🗄️</div>
        <p>暂无数据库连接，点击上方按钮添加</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>主机</th>
              <th>数据库</th>
              <th>用户名</th>
              <th>创建时间</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="conn in connections" :key="conn.id">
              <td style="font-weight: 500;">{{ conn.name }}</td>
              <td><span class="tag tag-blue">{{ conn.db_type }}</span></td>
              <td>{{ conn.host }}:{{ conn.port }}</td>
              <td>{{ conn.database_name }}</td>
              <td>{{ conn.username }}</td>
              <td style="font-size: 12px; color: var(--text-muted);">{{ new Date(conn.created_at).toLocaleString('zh-CN') }}</td>
              <td class="actions">
                <button class="btn btn-secondary btn-sm" :disabled="testing === conn.id" @click="handleTest(conn.id)">
                  {{ testing === conn.id ? '测试中...' : '测试' }}
                </button>
                <router-link :to="`/db-connections/${conn.id}/import`" class="btn btn-primary btn-sm" style="margin-left: 4px;">导入</router-link>
                <button class="btn btn-secondary btn-sm" style="margin-left: 4px;" @click="openEdit(conn)">编辑</button>
                <button class="btn btn-danger btn-sm" style="margin-left: 4px;" @click="handleDelete(conn.id, conn.name)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <!-- Test result -->
      <div v-if="testResult" style="margin-top: 12px; padding: 8px 12px; border-radius: 6px; font-size: 13px;"
        :style="{ background: testResult.success ? '#ecfdf5' : '#fef2f2', color: testResult.success ? '#059669' : '#dc2626' }">
        {{ testResult.message }}
      </div>
    </div>
  </div>
</template>
