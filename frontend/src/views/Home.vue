<script setup lang="ts">
</script>

<template>
  <div>
    <div class="page-header">
      <h2>平台概览</h2>
      <p>Ontology Service Platform 本体语义服务平台</p>
    </div>

    <div class="card">
      <div class="arch-diagram">
        <!-- Layer 1: API Gateway -->
        <div class="arch-layer gateway">
          <div class="arch-layer-title">API 网关层</div>
          <div class="arch-layer-desc">REST API (FastAPI)</div>
          <div class="arch-layer-sub">本体 CRUD · 导入导出 · 语义查询</div>
        </div>
        <div class="arch-arrow">↓</div>

        <!-- Layer 2: Ontology Management -->
        <div class="arch-layer management">
          <div class="arch-layer-title">本体管理层</div>
          <div class="arch-layer-desc">Ontology Management</div>
          <div class="arch-layer-sub">元模型定义 · 类层次结构 · 属性映射</div>
        </div>
        <div class="arch-arrow">↓</div>

        <!-- Layer 3: Mapping -->
        <div class="arch-layer mapping">
          <div class="arch-layer-title">语义映射层</div>
          <div class="arch-layer-desc">Semantic Mapping</div>
          <div class="arch-layer-sub">Class ↔ Table · Property ↔ Column</div>
        </div>
        <div class="arch-arrow">↓</div>

        <!-- Layer 4: Data Source -->
        <div class="arch-layer datasource">
          <div class="arch-layer-title">数据源层</div>
          <div class="arch-layer-desc">Data Source</div>
          <div class="arch-layer-sub">SQLite / PostgreSQL / MySQL</div>
        </div>
      </div>
    </div>

  </div>
</template>
