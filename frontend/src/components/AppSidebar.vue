<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const isActive = (path: string) => route.path === path
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <div class="logo-row">
        <svg class="logo" xmlns="http://www.w3.org/2000/svg" width="130" height="17" viewBox="0 0 160 21"><g fill="#c4261d"><path d="M.1 20.7h6l3.7-9.8H17l-3.6 9.8h4.8s.7 0 1.1-.4.6-.7.6-.7L27.1.3H21l-2.8 7.8h-7.3L13.8.3H7.6L.1 20.7z"/><path d="M23.6 20.7h6.1L37.2.3h-6zm10.3 0L41.5.3h6.1L44.7 8l10-7.7h4.5L49.1 8.1l6.5 12.6h-5.2s-.8 0-1.1-.3c-.5-.3-.7-.7-.7-.7l-4-7.8-.1-.1h-.1-.1l-1.4 1c-.1 0-2.9 7.9-2.9 7.9h-6.1z"/></g><g fill="#5f5e5c"><path d="M61.3.3l1.6 20.4h3.5s.5-.1.8-.3.7-.7.7-.7L80.9.3h-3.3l-9.4 14.1L67 .2l-5.7.1z"/><path d="M83.3.2l-7.5 20.5h5.7L89 .2zm28.1.1l-7.5 20.4h5.7L117.2.3z"/><path d="M85.2 20.7l1-2.6h9.4s.9 0 1.3-1c.3-.8.2-1.1.1-1.4 0-.1-.1-.5-.7-1.3-.7-.9-3.8-4.3-3.8-4.3s-1-1.3-1.2-2.1c0 0-.1-.3-.2-.9-.1-.4-.1-.8-.1-1 0-.8.2-2.8 1.9-4.3 0 0 1.4-1.5 4-1.5h10.5l-1 2.5h-8.6c-.1 0-.8 0-1.5.9 0 0-.2.3-.3.8 0 .4 0 .9.4 1.4 0 0 .9 1.3 2 2.3s2.6 2.7 2.6 2.7.6.8 1 1.6c.4.9.6 1.7.6 2.7 0 .5-.2 1.3-.4 1.9-.3.8-.9 1.6-1.3 2-.5.5-1.2 1.1-2 1.4-.9.2-1.9.2-1.9.2H85.2zm30.4-6.7s.3-1.3.8-2.6c.5-1.5 1.1-3 1.3-3.3.3-.7.8-1.8 1.4-2.7s1.1-1.4 1.1-1.4c1.1-1.3 2.5-2.3 4.1-3 1.8-.8 3.5-.8 3.5-.8h4.2s1.1 0 2.1.4a4.11 4.11 0 0 1 1.6 1.2c1.8 1.6 1.4 4.3 1.4 4.3l-1.1 4c-.2.6-.7 1.9-.9 2.3l-1.5 3c-.3.6-.6 1-.8 1.2l-1.2 1.4c-.4.5-.9.8-1.8 1.3-.6.4-1.1.6-1.6.8-1.4.6-3.4.8-3.4.8h-4.6s-1.1-.2-2-.5c-.4-.2-1-.5-1.3-.9a5.86 5.86 0 0 1-1.4-2.2c-.1-.3-.2-.7-.2-1V15c.2-.4.3-1 .3-1zm5 2.5c0 .2 0 .7.2 1.1s.5.8.7.9c.2.2.5.3 1 .3s1.4-.1 1.4-.1c1.3-.2 1.9-.7 2.6-1.3.6-.5 1.2-1.6 1.2-1.6l4-10.8s.1-.3.1-.6v-.5s-.1-.7-.7-1.2-1.3-.5-1.8-.5c-.6 0-1.1.1-1.5.2-.6.2-1.1.4-2 1.2-.7.6-1.2 1.6-1.2 1.6l-4 10.9c.2-.1 0 .1 0 .4zm14.5 4.2L142.7.2h3.7l5.3 13.6.1.2c.1 0 .1-.2.1-.2l5-13.6h3l-7.6 20.4h-2.7s-.5.1-1-.3-.7-.9-.7-.9l-4.7-12.6s0-.1-.1-.1-.1.1-.1.1l-5 13.8-2.9.1z"/></g></svg>
      </div>
      <div class="title-block">
        <h1>Ontology Service Platform</h1>
        <div class="sub">本体语义服务平台</div>
      </div>
    </div>
    <nav class="sidebar-nav">
      <div class="nav-section">
        <router-link to="/" class="nav-section-title" :class="{ active: isActive('/') }">
          <span class="icon">🏠</span>
          首页
        </router-link>
      </div>

      <div class="nav-section">
        <router-link to="/ontologies" class="nav-section-title" :class="{ active: isActive('/ontologies') }">
          <span class="icon">📚</span>
          本体管理
        </router-link>
      </div>

      <div class="nav-section">
        <router-link to="/query-test" class="nav-section-title" :class="{ active: isActive('/query-test') }">
          <span class="icon">🔍</span>
          语义查询
        </router-link>
      </div>

      <div class="nav-section">
        <router-link to="/agents" class="nav-section-title" :class="{ active: isActive('/agents') }">
          <span class="icon">🤖</span>
          Agent 交互
        </router-link>
      </div>

      <div class="nav-section">
        <router-link to="/services/semantic-query" class="nav-section-title" :class="{ active: isActive('/services/semantic-query') }">
          <span class="icon">🔌</span>
          对外服务
        </router-link>
      </div>

      <div class="nav-section">
        <router-link to="/model-configs" class="nav-section-title" :class="{ active: isActive('/model-configs') }">
          <span class="icon">⚙️</span>
          模型配置
        </router-link>
      </div>

      <div class="nav-section">
        <router-link to="/db-connections" class="nav-section-title" :class="{ active: isActive('/db-connections') }">
          <span class="icon">🗄️</span>
          数据库连接
        </router-link>
      </div>
    </nav>
    <div class="sidebar-footer">
      <div class="version-badge">支持 OWL 导出 · Manchester TBox</div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar-header {
  padding: 24px 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-row {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.logo {
  display: block;
  height: 18px;
  width: auto;
}

.title-block {
  text-align: center;
}

.title-block h1 {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
  letter-spacing: 0.3px;
  line-height: 1.3;
}

.title-block .sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border, #e2e8f0);
  margin-top: auto;
}
.version-badge {
  font-size: 11px;
  color: var(--text-muted, #94a3b8);
  text-align: center;
}
</style>
