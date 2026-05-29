<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getModelConfig, createModelConfig, updateModelConfig } from '../../api/modelConfig'

const router = useRouter()
const route = useRoute()
const isEdit = !!route.params.id

const form = ref({
  name: '',
  provider: 'deepseek',
  base_url: 'https://api.deepseek.com',
  api_key: '',
  model_name: 'deepseek-chat',
})
const saving = ref(false)
const error = ref('')

onMounted(async () => {
  if (isEdit) {
    try {
      const data = await getModelConfig(route.params.id as string)
      form.value.name = data.name
      form.value.provider = data.provider
      form.value.base_url = data.base_url
      form.value.model_name = data.model_name
    } catch (e: any) {
      error.value = e.response?.data?.detail || e.message
    }
  }
})

async function handleSubmit() {
  saving.value = true
  error.value = ''
  try {
    if (isEdit) {
      await updateModelConfig(route.params.id as string, form.value)
    } else {
      await createModelConfig(form.value)
    }
    router.push('/model-configs')
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h2>{{ isEdit ? '编辑' : '添加' }}模型配置</h2>
      <router-link to="/model-configs" class="btn btn-secondary">← 返回</router-link>
    </div>

    <div class="card">
      <div v-if="error" class="alert alert-error">{{ error }}</div>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>配置名称</label>
          <input v-model="form.name" required placeholder="例如：DeepSeek V3" />
        </div>
        <div class="form-group">
          <label>供应商</label>
          <select v-model="form.provider">
            <option value="deepseek">DeepSeek</option>
            <option value="openai">OpenAI</option>
            <option value="azure">Azure OpenAI</option>
            <option value="custom">自定义（OpenAI 兼容）</option>
          </select>
        </div>
        <div class="form-group">
          <label>API 地址</label>
          <input v-model="form.base_url" required placeholder="https://api.deepseek.com" />
        </div>
        <div class="form-group">
          <label>模型名称</label>
          <input v-model="form.model_name" required placeholder="deepseek-chat" />
        </div>
        <div class="form-group">
          <label>API Key</label>
          <input v-model="form.api_key" :required="!isEdit" type="password" placeholder="sk-..." />
          <small v-if="isEdit" style="color: var(--text-muted);">留空则不修改</small>
        </div>
        <div class="form-actions">
          <router-link to="/model-configs" class="btn btn-secondary">取消</router-link>
          <button type="submit" class="btn btn-primary" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-weight: 500; margin-bottom: 4px; color: var(--text-h); }
.form-group input, .form-group select { width: 100%; padding: 8px 12px; border: 1px solid var(--border); border-radius: var(--radius); outline: none; }
.form-group input:focus, .form-group select:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-light); }
.form-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 24px; }
</style>
