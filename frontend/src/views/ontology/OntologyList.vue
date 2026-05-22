<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listOntologies, deleteOntology, type OntologyListItem } from '../../api/ontology'

const router = useRouter()
const ontologies = ref<OntologyListItem[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    ontologies.value = await listOntologies()
  } catch (e: any) {
    error.value = e.message || 'Failed to load ontologies'
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: string, name: string) {
  if (!confirm(`确认删除本体 "${name}"？此操作不可恢复。`)) return
  try {
    await deleteOntology(id)
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
      <h2>本体列表</h2>
      <p>管理所有已创建的本体</p>
    </div>

    <div class="card">
      <div class="card-header">
        <h3>本体</h3>
        <div style="display: flex; gap: 6px;">
          <router-link to="/ontologies/import" class="btn btn-secondary">📥 导入</router-link>
          <router-link to="/ontologies/create" class="btn btn-primary">➕ 创建</router-link>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="alert alert-error">{{ error }}</div>
      <div v-else-if="ontologies.length === 0" class="empty-state">
        <div class="icon">📭</div>
        <p>暂无本体，点击上方按钮创建</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>命名空间</th>
              <th>版本</th>
              <th>类数量</th>
              <th>属性数量</th>
              <th style="min-width: 120px;">描述</th>
              <th>创建时间</th>
              <th class="actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in ontologies" :key="o.id">
              <td>
                <router-link :to="`/ontologies/${o.id}`" style="font-weight: 500; color: var(--accent);">
                  {{ o.name }}
                </router-link>
              </td>
              <td style="font-size: 12px; max-width: 200px; overflow: hidden; text-overflow: ellipsis;">
                {{ o.namespace }}
              </td>
              <td><span class="tag tag-blue">{{ o.version }}</span></td>
              <td>{{ o.class_count }}</td>
              <td>{{ o.property_count }}</td>
              <td style="color: var(--text-muted); font-size: 13px; max-width: 200px; overflow: hidden; text-overflow: ellipsis;">
                {{ o.description || '—' }}
              </td>
              <td style="font-size: 12px; color: var(--text-muted);">
                {{ new Date(o.created_at).toLocaleString('zh-CN') }}
              </td>
              <td class="actions">
                <router-link :to="`/ontologies/${o.id}`" class="btn btn-secondary btn-sm">详情</router-link>
                <button class="btn btn-danger btn-sm" @click="handleDelete(o.id, o.name)" style="margin-left: 4px;">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
