<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listModelConfigs, deleteModelConfig, type ModelConfigResponse } from '../../api/modelConfig'

const router = useRouter()
const configs = ref<ModelConfigResponse[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    configs.value = await listModelConfigs()
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: string, name: string) {
  if (!confirm(`确认删除模型配置 "${name}"？`)) return
  try {
    await deleteModelConfig(id)
    await load()
  } catch (e: any) {
    alert(e.response?.data?.detail || e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h2>模型连接配置</h2>
      <p>管理大模型 API 连接信息（DeepSeek / OpenAI 兼容）</p>
    </div>

    <div class="card">
      <div class="card-header">
        <h3>模型配置</h3>
        <router-link to="/model-configs/create" class="btn btn-primary">+ 添加</router-link>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="alert alert-error">{{ error }}</div>
      <div v-else-if="configs.length === 0" class="empty-state">
        <p>暂无模型配置，点击上方按钮添加</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>供应商</th>
              <th>API 地址</th>
              <th>模型名称</th>
              <th>API Key</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in configs" :key="c.id">
              <td style="font-weight: 500;">{{ c.name }}</td>
              <td><span class="tag" :class="c.provider === 'deepseek' ? 'tag-blue' : 'tag-green'">{{ c.provider }}</span></td>
              <td style="font-size: 12px; max-width: 200px; overflow: hidden; text-overflow: ellipsis;">{{ c.base_url }}</td>
              <td><code>{{ c.model_name }}</code></td>
              <td><code>{{ c.api_key_masked }}</code></td>
              <td class="actions">
                <router-link :to="`/model-configs/${c.id}/edit`" class="btn btn-secondary btn-sm">编辑</router-link>
                <button class="btn btn-danger btn-sm" @click="handleDelete(c.id, c.name)" style="margin-left: 4px;">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
