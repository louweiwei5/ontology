<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getAgent, createAgent, updateAgent } from '../../api/agent'
import { listModelConfigs, type ModelConfigResponse } from '../../api/modelConfig'
import { listOntologies, type OntologyListItem } from '../../api/ontology'

const router = useRouter()
const route = useRoute()
const isEdit = !!route.params.id

const form = ref({
  name: '',
  description: '',
  system_prompt: '',
  ontology_id: '',
  model_config_id: '',
})
const ontologies = ref<OntologyListItem[]>([])
const modelConfigs = ref<ModelConfigResponse[]>([])
const saving = ref(false)
const error = ref('')

// Default system prompt template
const defaultPrompt = `You are an ontology-based data query assistant. You help users query structured data by generating DSL queries based on the ontology.

Your job:
1. Understand the user's question in natural language
2. Map it to the ontology classes and properties defined in the Ontology Context below
3. Generate a valid DSL query to retrieve the requested data
4. Present the results to the user in a clear, readable format`

async function loadForm() {
  error.value = ''
  try {
    ontologies.value = await listOntologies()
    modelConfigs.value = await listModelConfigs()

    if (isEdit) {
      const data = await getAgent(route.params.id as string)
      form.value.name = data.name
      form.value.description = data.description || ''
      form.value.system_prompt = data.system_prompt || ''
      form.value.ontology_id = data.ontology_id || ''
      form.value.model_config_id = data.model_config_id || ''
    } else {
      form.value.system_prompt = defaultPrompt
    }
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
}

async function handleSubmit() {
  saving.value = true
  error.value = ''
  try {
    if (isEdit) {
      await updateAgent(route.params.id as string, {
        ...form.value,
        ontology_id: form.value.ontology_id || undefined,
        model_config_id: form.value.model_config_id || undefined,
      })
    } else {
      await createAgent({
        ...form.value,
        ontology_id: form.value.ontology_id || undefined,
        model_config_id: form.value.model_config_id || undefined,
      })
    }
    router.push('/agents')
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    saving.value = false
  }
}

onMounted(loadForm)
</script>

<template>
  <div>
    <div class="page-header">
      <h2>{{ isEdit ? '编辑' : '创建' }} Agent</h2>
      <router-link to="/agents" class="btn btn-secondary">← 返回</router-link>
    </div>

    <div class="card">
      <div v-if="error" class="alert alert-error">{{ error }}</div>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>Agent 名称 <span class="required">*</span></label>
          <input v-model="form.name" required placeholder="例如：产品查询助手" />
        </div>

        <div class="form-group">
          <label>描述</label>
          <input v-model="form.description" placeholder="简短描述此 Agent 的用途" />
        </div>

        <div class="form-row">
          <div class="form-group" style="flex: 1;">
            <label>关联本体</label>
            <select v-model="form.ontology_id">
              <option value="">— 不选择 —</option>
              <option v-for="o in ontologies" :key="o.id" :value="o.id">
                {{ o.name }} (v{{ o.version }})
              </option>
            </select>
          </div>
          <div class="form-group" style="flex: 1;">
            <label>模型配置 <span class="required">*</span></label>
            <select v-model="form.model_config_id" required>
              <option value="">— 请选择 —</option>
              <option v-for="m in modelConfigs" :key="m.id" :value="m.id">
                {{ m.name }} ({{ m.model_name }})
              </option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label>系统提示词 (System Prompt)</label>
          <textarea v-model="form.system_prompt" rows="10"
            placeholder="Agent 的系统提示词，用于指导 LLM 行为和 DSL 生成"></textarea>
          <small style="color: var(--text-muted);">
            提示词中会自动追加本体 TBox 上下文和 DSL 输出格式说明
          </small>
        </div>

        <div class="form-actions">
          <router-link to="/agents" class="btn btn-secondary">取消</router-link>
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
.form-group input, .form-group select, .form-group textarea {
  width: 100%; padding: 8px 12px; border: 1px solid var(--border); border-radius: var(--radius); outline: none;
}
.form-group textarea { resize: vertical; font-family: 'SF Mono', 'Fira Code', monospace; font-size: 13px; }
.form-group input:focus, .form-group select:focus, .form-group textarea:focus {
  border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-light);
}
.form-row { display: flex; gap: 16px; }
.required { color: var(--danger); }
.form-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 24px; }
</style>
