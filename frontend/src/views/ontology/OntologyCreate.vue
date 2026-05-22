<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createOntology } from '../../api/ontology'

const router = useRouter()

const form = ref({
  name: '',
  namespace: '',
  description: '',
  version: '1.0.0',
})

const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  if (!form.value.name.trim()) {
    error.value = '本体名称不能为空'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await createOntology({
      name: form.value.name.trim(),
      namespace: form.value.namespace.trim() || undefined,
      description: form.value.description.trim() || undefined,
      version: form.value.version.trim() || undefined,
    })
    router.push(`/ontologies/${result.id}`)
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
      <h2>创建本体</h2>
      <p>创建一个新的本体定义</p>
    </div>

    <div class="card" style="max-width: 600px;">
      <div v-if="error" class="alert alert-error">{{ error }}</div>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>本体名称 <span style="color: var(--danger);">*</span></label>
          <input v-model="form.name" class="form-input" placeholder="例如：organization" required />
          <div class="hint">唯一标识符，创建后不可与其他本体重名</div>
        </div>
        <div class="form-group">
          <label>命名空间</label>
          <input v-model="form.namespace" class="form-input" placeholder="例如：http://example.org/org" />
          <div class="hint">可选，默认自动生成</div>
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="form.description" class="form-input" placeholder="本体的用途说明" />
        </div>
        <div class="form-group">
          <label>版本</label>
          <input v-model="form.version" class="form-input" placeholder="1.0.0" />
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">
            {{ loading ? '创建中...' : '创建' }}
          </button>
          <router-link to="/ontologies" class="btn btn-secondary">取消</router-link>
        </div>
      </form>
    </div>
  </div>
</template>
