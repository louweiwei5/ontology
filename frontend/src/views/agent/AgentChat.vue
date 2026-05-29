<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAgent, type AgentDetail } from '../../api/agent'
import { sendMessage, getSessions, getSessionMessages, deleteSession, type ChatMessage, type ChatSession, type QueryResult } from '../../api/chat'

const route = useRoute()
const router = useRouter()
const agentId = route.params.id as string

// State
const agent = ref<AgentDetail | null>(null)
const messages = ref<ChatMessage[]>([])
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<string>('')
const inputText = ref('')
const sending = ref(false)
const loading = ref(true)
const showSessions = ref(true)
const error = ref('')

// Load agent info
async function loadAgent() {
  try {
    agent.value = await getAgent(agentId)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  }
}

// Load sessions
async function loadSessions() {
  try {
    sessions.value = await getSessions(agentId)
  } catch (_) {}
}

// Load messages for a session
async function loadSessionMessages(sessionId: string) {
  currentSessionId.value = sessionId
  messages.value = []
  try {
    messages.value = await getSessionMessages(agentId, sessionId)
  } catch (_) {}
  await nextTick()
  scrollToBottom()
}

// Send message
async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  inputText.value = ''
  sending.value = true

  // Optimistically add user message
  messages.value.push({
    id: 'temp',
    role: 'user',
    content: text,
    dsl_query: null,
    query_result: null,
    created_at: new Date().toISOString(),
  })
  scrollToBottom()

  try {
    const res = await sendMessage(agentId, text, currentSessionId.value || undefined)

    // Set session ID from response
    if (!currentSessionId.value) {
      currentSessionId.value = res.session_id
      loadSessions()
    }

    // Replace temp user message with real one, add assistant message
    const msgs = await getSessionMessages(agentId, currentSessionId.value)
    messages.value = msgs
  } catch (e: any) {
    // Mark the temp message as failed
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.id === 'temp') {
      lastMsg.content += '\n\n⚠️ 发送失败：' + (e.response?.data?.detail || e.message)
      lastMsg.id = 'error-' + Date.now()
    }
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

// Start new session
function newSession() {
  currentSessionId.value = ''
  messages.value = []
  inputText.value = ''
}

// Switch session
function switchSession(sessionId: string) {
  if (sessionId === currentSessionId.value) return
  loadSessionMessages(sessionId)
}

// Delete session
async function handleDeleteSession(sessionId: string) {
  if (!confirm('确认删除此会话？')) return
  try {
    await deleteSession(agentId, sessionId)
    if (currentSessionId.value === sessionId) {
      newSession()
    }
    await loadSessions()
  } catch (e: any) {
    console.error('Failed to delete session:', e)
  }
}

// Scroll to bottom
function scrollToBottom() {
  const container = document.querySelector('.chat-messages')
  if (container) container.scrollTop = container.scrollHeight
}

// Watch for route param changes
watch(() => route.params.id, () => {
  location.reload()
})

// JSON display helpers
function formatJson(obj: any): string {
  return JSON.stringify(obj, null, 2)
}

// Show prompt message size info
function formatPromptSize(content: string): string {
  const len = content.length
  if (len < 1000) return len + ' chars'
  if (len < 100000) return (len / 1000).toFixed(1) + 'K chars'
  return (len / 1000).toFixed(0) + 'K chars'
}

// Check if a value is complex (array or object) for nested rendering
function isComplexValue(val: any): boolean {
  return val !== null && val !== undefined && typeof val === 'object'
}

// Field label lookup from ontology metadata
function fieldLabel(key: string, labels?: Record<string, string>): string {
  return labels?.[key] || key
}

// Get scalar (non-relation) fields from a row
function getScalarFields(row: Record<string, any>, relTypes?: Record<string, string>, labels?: Record<string, string>): { key: string; label: string; value: any }[] {
  const fields: { key: string; label: string; value: any }[] = []
  for (const [key, val] of Object.entries(row)) {
    // Skip relation fields and complex values (arrays, objects) not in relation_types
    if (relTypes?.[key]) continue
    if (isComplexValue(val)) continue
    fields.push({ key, label: fieldLabel(key, labels), value: val })
  }
  return fields
}

// Check if relation type is a single-object relation (merge into parent)
function isSingleRelation(relType?: string): boolean {
  return relType === 'one-to-one' || relType === 'many-to-one'
}

// Merged fields from single-object relations (one-to-one, many-to-one)
function getOtoMergedFields(row: Record<string, any>, relTypes?: Record<string, string>, labels?: Record<string, string>): { key: string; label: string; value: any }[] {
  const fields: { key: string; label: string; value: any }[] = []
  for (const [key, val] of Object.entries(row)) {
    if (isSingleRelation(relTypes?.[key]) && Array.isArray(val) && val.length > 0) {
      const relLabel = fieldLabel(key, labels)
      for (const [subKey, subVal] of Object.entries(val[0])) {
        // Only include scalar sub-values; skip complex nested structures
        if (!isComplexValue(subVal)) {
          fields.push({
            key: `${key}.${subKey}`,
            label: `${relLabel} / ${fieldLabel(subKey, labels)}`,
            value: subVal
          })
        }
      }
    }
  }
  return fields
}

// Collection-type relation data (one-to-many, many-to-many)
function getOtmRelations(row: Record<string, any>, relTypes?: Record<string, string>, labels?: Record<string, string>): { name: string; label: string; items: Record<string, any>[]; columns: string[] }[] {
  const rels: { name: string; label: string; items: Record<string, any>[]; columns: string[] }[] = []
  for (const [key, val] of Object.entries(row)) {
    if (relTypes?.[key] && !isSingleRelation(relTypes[key]) && Array.isArray(val)) {
      const items = val as Record<string, any>[]
      const cols = items.length > 0 ? Object.keys(items[0]) : []
      rels.push({ name: key, label: fieldLabel(key, labels), items, columns: cols })
    }
  }
  return rels
}

// Check if any row has complex values
function hasNestedData(rows: any[] | null | undefined, relTypes?: Record<string, string>): boolean {
  if (!rows || rows.length === 0) return false
  return rows.some(row => Object.keys(row).some(k => relTypes?.[k]))
}

onMounted(async () => {
  loading.value = true
  await loadAgent()
  await loadSessions()
  loading.value = false
})
</script>

<template>
  <div class="chat-page">
    <!-- Agent header -->
    <div class="chat-header">
      <div class="chat-header-left">
        <button class="btn btn-sm btn-ghost" @click="router.push('/agents')">← Agent 列表</button>
        <div v-if="agent" class="agent-info">
          <strong>{{ agent.name }}</strong>
          <span v-if="agent.ontology_name" class="tag tag-blue-sm">{{ agent.ontology_name }}</span>
        </div>
      </div>
      <div class="chat-header-right">
        <button class="btn btn-sm btn-ghost" @click="showSessions = !showSessions">
          {{ showSessions ? '隐藏会话' : '会话列表' }}
        </button>
        <button class="btn btn-sm btn-secondary" @click="newSession">+ 新对话</button>
      </div>
    </div>

    <div class="chat-body">
      <!-- Session sidebar -->
      <div v-if="showSessions" class="session-sidebar">
        <div class="session-list-header">
          <h4>历史会话</h4>
        </div>
        <div v-if="sessions.length === 0" class="session-empty">暂无历史会话</div>
        <div v-else class="session-list">
          <div v-for="s in sessions" :key="s.session_id"
            class="session-item"
            :class="{ active: s.session_id === currentSessionId }"
            @click="switchSession(s.session_id)">
            <div class="session-preview">{{ s.preview || '新会话' }}</div>
            <div class="session-meta">
              <span>{{ s.message_count }} 条消息</span>
              <button class="btn-del" @click.stop="handleDeleteSession(s.session_id)" title="删除">×</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Chat area -->
      <div class="chat-main">
        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="error" class="alert alert-error">{{ error }}</div>
        <template v-else>
          <!-- Messages -->
          <div class="chat-messages" ref="messagesRef">
            <div v-if="messages.length === 0 && !loading" class="chat-welcome">
              <div class="welcome-icon">🤖</div>
              <h3>{{ agent?.name || 'Agent' }}</h3>
              <p>{{ agent?.description || '开始对话，输入自然语言查询数据' }}</p>
              <div v-if="agent?.ontology_name" class="welcome-tags">
                <span class="tag tag-blue">本体: {{ agent.ontology_name }}</span>
                <span class="tag tag-green">模型: {{ agent.model_config_name }}</span>
              </div>
            </div>

            <div v-for="msg in messages" :key="msg.id" class="message-group">
              <!-- User message -->
              <div v-if="msg.role === 'user'" class="message message-user">
                <div class="bubble bubble-user">
                  <div class="msg-text">{{ msg.content }}</div>
                </div>
              </div>

              <!-- Assistant message -->
              <div v-else class="message message-assistant">
                <div class="assistant-avatar">🤖</div>
                <div class="bubble bubble-assistant">
                  <div class="msg-text" v-html="msg.content.replace(/\n/g, '<br>')"></div>

                  <!-- DSL Query collapsible -->
                  <div v-if="msg.dsl_query" class="dsl-section">
                    <details>
                      <summary class="dsl-toggle">📋 查看 DSL 查询语句</summary>
                      <pre class="dsl-code"><code>{{ formatJson(msg.dsl_query) }}</code></pre>
                    </details>
                  </div>

                  <!-- SQL (generated from DSL) -->
                  <div v-if="msg.query_result?.sql" class="dsl-section">
                    <details>
                      <summary class="dsl-toggle">🗄️ 查看生成的 SQL</summary>
                      <pre class="sql-code"><code>{{ msg.query_result.sql }}</code></pre>
                    </details>
                  </div>

                  <!-- DSL error diagnostics -->
                  <div v-if="msg.query_result?.dsl_error" class="diagnostics-section">
                    <details open>
                      <summary class="diagnostics-toggle">⚠️ 查询诊断信息</summary>
                      <div class="diagnostics-error">
                        <div class="diag-label">错误信息</div>
                        <pre class="diag-pre">{{ msg.query_result.dsl_error }}</pre>
                      </div>
                    </details>
                  </div>

                  <!-- LLM Prompt context -->
                  <div v-if="msg.llm_prompt && msg.llm_prompt.length > 0" class="dsl-section">
                    <details>
                      <summary class="dsl-toggle">🧠 查看发送给 LLM 的完整 Prompt ({{ msg.llm_prompt.length }} 条消息)</summary>
                      <div class="prompt-context">
                        <div v-for="(m, mi) in msg.llm_prompt" :key="mi" class="prompt-message" :class="'prompt-' + m.role">
                          <div class="prompt-role-tag" :class="'role-' + m.role">
                            <template v-if="m.role === 'system'">🖥️ System ({{ formatPromptSize(m.content) }})</template>
                            <template v-else-if="m.role === 'user'">👤 User</template>
                            <template v-else>🤖 Assistant</template>
                          </div>
                          <pre class="prompt-content"><code>{{ m.content }}</code></pre>
                        </div>
                      </div>
                    </details>
                  </div>

                  <!-- Query result -->
                  <div v-if="msg.query_result && !msg.query_result.dsl_error" class="result-section">
                    <div class="result-header">
                      📊 查询结果
                      <span v-if="msg.query_result.total > 0" class="result-count">
                        ({{ msg.query_result.rows?.length || 0 }} / {{ msg.query_result.total }} 条)
                      </span>
                    </div>

                    <!-- Data with relation structures -->
                    <div v-if="msg.query_result.rows && msg.query_result.rows.length > 0 && hasNestedData(msg.query_result.rows, msg.query_result.relation_types)" class="result-entity-wrap">
                      <div v-for="(row, ri) in msg.query_result.rows" :key="ri" class="result-entity-card">
                        <details :open="ri < 10">
                          <summary class="entity-card-header">
                            记录 {{ ri + 1 }}
                          </summary>
                          <div class="entity-card-body">
                            <!-- Scalar fields -->
                            <div class="entity-fields">
                              <div v-for="f in getScalarFields(row, msg.query_result.relation_types, msg.query_result.field_labels)" :key="f.key" class="entity-field-row">
                                <span class="ef-label">{{ f.label }}</span>
                                <span class="ef-value">{{ f.value ?? '—' }}</span>
                              </div>
                            </div>

                            <!-- One-to-one merged fields -->
                            <div v-if="getOtoMergedFields(row, msg.query_result.relation_types, msg.query_result.field_labels).length > 0" class="entity-fields entity-fields-oto">
                              <div class="oto-divider">┄ 关联属性 ┄</div>
                              <div v-for="f in getOtoMergedFields(row, msg.query_result.relation_types, msg.query_result.field_labels)" :key="f.key" class="entity-field-row">
                                <span class="ef-label ef-label-sub">{{ f.label }}</span>
                                <span class="ef-value">{{ f.value ?? '—' }}</span>
                              </div>
                            </div>

                            <!-- One-to-many sub-tables -->
                            <div v-for="rel in getOtmRelations(row, msg.query_result.relation_types, msg.query_result.field_labels)" :key="rel.name" class="relation-subsection">
                              <div class="relation-subtitle">{{ rel.label }} ({{ rel.items.length }} 条)</div>
                              <div v-if="rel.items.length > 0" class="result-table-wrap">
                                <table class="result-table">
                                  <thead>
                                    <tr>
                                      <th v-for="col in rel.columns" :key="col">{{ fieldLabel(col, msg.query_result.field_labels) }}</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    <tr v-for="(item, ii) in rel.items" :key="ii">
                                      <td v-for="col in rel.columns" :key="col">{{ item[col] ?? '—' }}</td>
                                    </tr>
                                  </tbody>
                                </table>
                              </div>
                              <div v-else class="relation-empty">无关联数据</div>
                            </div>
                          </div>
                        </details>
                      </div>
                    </div>

                    <!-- Flat data: render as table -->
                    <div v-else-if="msg.query_result.columns && msg.query_result.columns.length > 0 && msg.query_result.rows && msg.query_result.rows.length > 0" class="result-table-wrap">
                      <table class="result-table">
                        <thead>
                          <tr>
                            <th v-for="col in msg.query_result.columns" :key="col">{{ fieldLabel(col, msg.query_result.field_labels) }}</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, ri) in msg.query_result.rows" :key="ri">
                            <td v-for="col in msg.query_result.columns" :key="col">{{ row[col] ?? '—' }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                    <div v-else class="result-empty">
                      没有查询到数据
                    </div>
                    <div v-if="msg.query_result.message" class="result-message">
                      {{ msg.query_result.message }}
                    </div>
                  </div>

                  <!-- Failure state -->
                  <div v-if="msg.query_result?.dsl_error" class="result-section result-failure">
                    <div class="result-header result-header-error">❌ 查询执行失败</div>
                    <div class="result-empty">{{ msg.query_result.message || 'DSL 查询执行失败' }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Typing indicator -->
            <div v-if="sending" class="message message-assistant">
              <div class="assistant-avatar">🤖</div>
              <div class="bubble bubble-assistant">
                <div class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          </div>

          <!-- Input area -->
          <div class="chat-input-area">
            <div class="input-wrapper">
              <textarea
                v-model="inputText"
                placeholder="输入您的查询问题..."
                @keydown.enter.prevent="handleSend"
                rows="2"
                :disabled="sending"
              ></textarea>
              <button class="btn btn-primary send-btn" @click="handleSend" :disabled="sending || !inputText.trim()">
                {{ sending ? '...' : '发送' }}
              </button>
            </div>
            <div class="input-hint">Enter 发送，Shift+Enter 换行</div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
  margin: -24px;
  background: var(--bg);
}

/* Header */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.chat-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.chat-header-right {
  display: flex;
  gap: 8px;
}
.agent-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tag-blue-sm {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #eef2ff;
  color: #6366f1;
}

/* Body */
.chat-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* Session sidebar */
.session-sidebar {
  width: 260px;
  border-right: 1px solid var(--border);
  background: var(--card-bg);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.session-list-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.session-list-header h4 {
  font-size: 13px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.session-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}
.session-list {
  overflow-y: auto;
  flex: 1;
}
.session-item {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border);
  transition: background 0.15s;
}
.session-item:hover { background: var(--bg); }
.session-item.active {
  background: var(--accent-light);
  border-left: 3px solid var(--accent);
}
.session-preview {
  font-size: 13px;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}
.session-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-muted);
}
.btn-del {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 16px;
  padding: 0 4px;
}
.btn-del:hover { color: var(--danger); }

/* Chat main */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* Messages area */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Welcome */
.chat-welcome {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
}
.welcome-icon { font-size: 48px; margin-bottom: 12px; }
.chat-welcome h3 { font-size: 18px; color: var(--text-h); margin-bottom: 4px; }
.chat-welcome p { font-size: 14px; margin-bottom: 12px; }
.welcome-tags { display: flex; gap: 8px; justify-content: center; }

/* Messages */
.message-group { display: flex; flex-direction: column; gap: 8px; }
.message { display: flex; gap: 8px; max-width: 90%; }
.message-user { align-self: flex-end; }
.message-assistant { align-self: flex-start; }

.assistant-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.5;
  font-size: 14px;
  max-width: 100%;
  min-width: 0;
}
.bubble-user {
  background: var(--accent);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble-assistant {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
  max-width: min(720px, calc(100vw - 340px));
  overflow-x: hidden;
}

.msg-text { white-space: pre-wrap; word-break: break-word; }

/* DSL section */
.dsl-section { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border); }
.dsl-toggle {
  cursor: pointer;
  font-size: 12px;
  color: var(--accent);
  user-select: none;
}
.dsl-code {
  margin-top: 6px;
  padding: 10px;
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
}
.sql-code {
  margin-top: 6px;
  padding: 10px;
  background: #0f172a;
  color: #a5f3fc;
  border-radius: 6px;
  font-size: 12px;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* Diagnostics section */
.diagnostics-section { margin-top: 10px; padding-top: 10px; border-top: 1px solid #fecaca; }
.diagnostics-toggle {
  cursor: pointer;
  font-size: 12px;
  color: #dc2626;
  user-select: none;
  font-weight: 500;
}
.diagnostics-error { margin-top: 6px; }
.diag-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 2px;
}
.diag-pre {
  padding: 8px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  font-size: 12px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  color: #991b1b;
  line-height: 1.4;
}

/* Result section */
.result-section { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border); }
.result-failure { border-top-color: #fecaca; }
.result-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-h);
  margin-bottom: 6px;
}
.result-header-error { color: #dc2626; }
.result-count { font-weight: 400; color: var(--text-muted); }

/* Entity card display */
.result-entity-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 500px;
  overflow-y: auto;
  max-width: 100%;
}
.result-entity-card {
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
}
.entity-card-header {
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  padding: 7px 12px;
  user-select: none;
  color: var(--text-h);
}
.entity-card-body {
  padding: 0 12px 10px;
  border-top: 1px solid var(--border);
}
.entity-fields {
  padding: 6px 0;
}
.entity-fields-oto {
  background: #f8f9ff;
  margin: 0 -12px;
  padding: 4px 12px;
  border-top: 1px dashed #d0d5e7;
  border-bottom: 1px dashed #d0d5e7;
}
.oto-divider {
  font-size: 10px;
  color: var(--text-muted);
  text-align: center;
  padding: 2px 0;
  letter-spacing: 2px;
}
.entity-field-row {
  display: flex;
  gap: 8px;
  padding: 3px 0;
  font-size: 12px;
  line-height: 1.5;
}
.ef-label {
  font-weight: 500;
  color: var(--text-muted);
  min-width: 90px;
  flex-shrink: 0;
}
.ef-label-sub { color: #6366f1; }
.ef-value {
  color: var(--text);
  word-break: break-all;
}
.relation-subsection {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}
.relation-subtitle {
  font-size: 12px;
  font-weight: 500;
  color: var(--accent);
  margin-bottom: 4px;
}
.relation-empty {
  font-size: 11px;
  color: var(--text-muted);
  padding: 4px 0;
}

.result-table-wrap { overflow-x: auto; max-width: 100%; }
.result-table { min-width: 0; }
.result-entity-card { max-width: 100%; overflow: hidden; }
.entity-card-body { max-width: 100%; overflow: hidden; }
.relation-subsection { max-width: 100%; overflow: hidden; }
.result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.result-table th {
  background: var(--bg);
  padding: 6px 10px;
  text-align: left;
  font-weight: 500;
  border-bottom: 2px solid var(--border);
  white-space: nowrap;
}
.result-table td {
  padding: 5px 10px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.result-table tbody tr:hover { background: var(--accent-light); }
.result-empty {
  padding: 12px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}
.result-message {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}
.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  animation: typing 1.4s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-4px); }
}

/* Input area */
.chat-input-area {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--border);
  background: var(--card-bg);
  flex-shrink: 0;
}
.input-wrapper {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.input-wrapper textarea {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 10px;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.5;
  max-height: 120px;
}
.input-wrapper textarea:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-light);
}
.send-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 10px;
  flex-shrink: 0;
}
.input-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  padding-left: 4px;
}

/* Utility */
.btn-ghost {
  background: none;
  border: 1px solid transparent;
  color: var(--text-muted);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  transition: all 0.15s;
}
.btn-ghost:hover {
  background: var(--bg);
  color: var(--text);
  border-color: var(--border);
}
</style>
