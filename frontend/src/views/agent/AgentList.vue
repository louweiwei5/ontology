<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listAgents, deleteAgent, type AgentListItem } from '../../api/agent'

const router = useRouter()
const agents = ref<AgentListItem[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    agents.value = await listAgents()
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: string, name: string) {
  if (!confirm(`确认删除 Agent "${name}"？`)) return
  try {
    await deleteAgent(id)
    await load()
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

function handleChat(id: string) {
  router.push(`/agents/${id}/chat`)
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h2>Agent 管理</h2>
      <p>创建和管理基于本体的智能对话 Agent</p>
    </div>

    <div class="card">
      <div class="card-header">
        <h3>Agent 列表</h3>
        <router-link to="/agents/create" class="btn btn-primary">+ 创建 Agent</router-link>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="alert alert-error">{{ error }}</div>
      <div v-else-if="agents.length === 0" class="empty-state">
        <p>暂无 Agent，点击上方按钮创建</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>描述</th>
              <th>关联本体</th>
              <th>模型配置</th>
              <th>创建时间</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in agents" :key="a.id">
              <td style="font-weight: 500; color: var(--accent);">
                <span style="cursor: pointer;" @click="handleChat(a.id)">{{ a.name }}</span>
              </td>
              <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis; color: var(--text-muted); font-size: 13px;">
                {{ a.description || '—' }}
              </td>
              <td><span class="tag tag-blue">{{ a.ontology_name || '—' }}</span></td>
              <td><span class="tag tag-green">{{ a.model_config_name || '—' }}</span></td>
              <td style="font-size: 12px; color: var(--text-muted);">{{ new Date(a.created_at).toLocaleString('zh-CN') }}</td>
              <td class="actions">
                <button class="btn btn-accent btn-sm" @click="handleChat(a.id)">对话</button>
                <router-link :to="`/agents/${a.id}/edit`" class="btn btn-secondary btn-sm" style="margin-left: 4px;">编辑</router-link>
                <button class="btn btn-danger btn-sm" @click="handleDelete(a.id, a.name)" style="margin-left: 4px;">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
