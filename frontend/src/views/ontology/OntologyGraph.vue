<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as d3 from 'd3'
import { getOntologyGraph, type GraphNode, type GraphEdge } from '../../api/ontology'

const props = defineProps<{ ontologyId: string }>()
const router = useRouter()

const svgRef = ref<SVGSVGElement | null>(null)
const loading = ref(true)
const error = ref('')
const tooltip = ref<{ x: number; y: number; text: string; visible: boolean }>({
  x: 0, y: 0, text: '', visible: false,
})

let simulation: d3.Simulation<d3.SimulationNodeDatum, undefined> | null = null

onMounted(() => load())
onUnmounted(() => { simulation?.stop() })

watch(() => props.ontologyId, () => load())

async function load() {
  if (!props.ontologyId) return
  loading.value = true
  error.value = ''

  // Make sure the SVG ref is available
  await nextTick()
  if (!svgRef.value) {
    // Retry once after another tick
    await nextTick()
    if (!svgRef.value) {
      error.value = 'SVG 元素未就绪'
      loading.value = false
      return
    }
  }

  try {
    const data = await getOntologyGraph(props.ontologyId)
    if (data.nodes.length === 0) {
      error.value = '该本体中没有类，无法生成图形'
      loading.value = false
      return
    }
    renderGraph(data.nodes, data.edges)
  } catch (e: any) {
    error.value = e.response?.data?.detail || e.message
  } finally {
    loading.value = false
  }
}

function renderGraph(nodes: GraphNode[], edges: GraphEdge[]) {
  const svgEl = svgRef.value!
  const width = Math.max(svgEl.clientWidth, 600)
  const height = Math.max(500, window.innerHeight - 300)

  // Clear previous content
  d3.select(svgEl).selectAll('*').remove()
  d3.select(svgEl).attr('width', width).attr('height', height)

  // Root group for zoom
  const g = d3.select(svgEl).append('g')

  // Zoom
  const zoom = d3.zoom<SVGSVGElement, unknown>()
    .extent([[0, 0], [width, height]])
    .scaleExtent([0.2, 4])
    .on('zoom', (event) => g.attr('transform', event.transform))
  d3.select(svgEl).call(zoom)

  // Arrow markers
  const defs = d3.select(svgEl).append('defs')
  defs.append('marker')
    .attr('id', 'arrow-hierarchy')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 28).attr('refY', 0)
    .attr('markerWidth', 8).attr('markerHeight', 8).attr('orient', 'auto')
    .append('path').attr('d', 'M0,-5L10,0L0,5').attr('fill', '#6366f1')

  defs.append('marker')
    .attr('id', 'arrow-property')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 28).attr('refY', 0)
    .attr('markerWidth', 8).attr('markerHeight', 8).attr('orient', 'auto')
    .append('path').attr('d', 'M0,-5L10,0L0,5').attr('fill', '#f59e0b')

  // Data
  const d3Nodes: any[] = nodes.map(n => ({ ...n }))
  const d3Edges: any[] = edges.map(e => ({
    source: e.source, target: e.target, label: e.label, type: e.type,
  }))

  // Links
  const link = g.append('g').selectAll('line').data(d3Edges).join('line')
    .attr('stroke', d => d.type === 'hierarchy' ? '#6366f1' : '#f59e0b')
    .attr('stroke-width', 2).attr('stroke-opacity', 0.6)
    .attr('marker-end', d => d.type === 'hierarchy' ? 'url(#arrow-hierarchy)' : 'url(#arrow-property)')

  // Edge labels
  const edgeLabel = g.append('g').selectAll('text')
    .data(d3Edges.filter((e: any) => e.label)).join('text')
    .text((d: any) => d.label)
    .attr('font-size', 11).attr('fill', '#64748b')
    .attr('text-anchor', 'middle').attr('dy', -6)

  // Nodes
  const node = g.append('g').selectAll('g').data(d3Nodes).join('g')
    .call(d3.drag<any, any>()
      .on('start', (event, d) => {
        if (!event.active) simulation?.alphaTarget(0.3).restart()
        d.fx = d.x; d.fy = d.y
      })
      .on('drag', (event, d) => { d.fx = event.x; d.fy = event.y })
      .on('end', (event, d) => {
        if (!event.active) simulation?.alphaTarget(0)
        d.fx = null; d.fy = null
      }))

  node.append('circle')
    .attr('r', 24).attr('fill', '#eef2ff')
    .attr('stroke', '#6366f1').attr('stroke-width', 2)
    .style('cursor', 'grab')

  node.append('text')
    .text((d: any) => d.name)
    .attr('text-anchor', 'middle').attr('dy', 4)
    .attr('font-size', 12).attr('font-weight', 600)
    .attr('fill', '#4338ca').style('pointer-events', 'none')

  // Expand button + data property badges (hidden by default)
  node.each(function (d: any) {
    const grp = d3.select(this)
    const props = d.dataProperties as string[] | undefined
    if (props?.length) {
      // Badges group — initially hidden
      const badges = grp.append('g').attr('class', 'prop-badges').style('display', 'none')
      props.forEach((p: string, i: number) => {
        badges.append('rect')
          .attr('x', 28).attr('y', -8 + i * 18)
          .attr('width', p.length * 7 + 10).attr('height', 16)
          .attr('rx', 4).attr('fill', '#fff7ed')
          .attr('stroke', '#f59e0b').attr('stroke-width', 1)
        badges.append('text')
          .attr('x', 33).attr('y', 4 + i * 18)
          .attr('font-size', 10).attr('fill', '#d97706').text(p)
      })

      // Expand / collapse button
      const btnG = grp.append('g').attr('class', 'expand-btn').style('cursor', 'pointer')
      btnG.append('circle')
        .attr('cx', 32).attr('cy', 22).attr('r', 7)
        .attr('fill', '#e0e7ff').attr('stroke', '#6366f1').attr('stroke-width', 1)
      const btnText = btnG.append('text')
        .attr('x', 32).attr('y', 25).attr('text-anchor', 'middle')
        .attr('font-size', 9).attr('fill', '#4338ca')
        .attr('font-weight', 'bold').style('pointer-events', 'none').text('+')

      btnG.on('click', (event: MouseEvent) => {
        event.stopPropagation()
        d._expanded = !d._expanded
        badges.style('display', d._expanded ? '' : 'none')
        btnText.text(d._expanded ? '−' : '+')
      })
    }
  })

  // Hover
  node.on('mouseenter', function (event: MouseEvent, d: any) {
    const connected = new Set<string>([d.id])
    d3Edges.forEach((e: any) => {
      const s = typeof e.source === 'object' ? e.source.id : e.source
      const t = typeof e.target === 'object' ? e.target.id : e.target
      if (s === d.id) connected.add(t)
      if (t === d.id) connected.add(s)
    })
    node.style('opacity', (n: any) => connected.has(n.id) ? 1 : 0.2)
    link.style('opacity', (e: any) => {
      const s = typeof e.source === 'object' ? e.source.id : e.source
      const t = typeof e.target === 'object' ? e.target.id : e.target
      return (s === d.id || t === d.id) ? 1 : 0.1
    })
    edgeLabel.style('opacity', (e: any) => {
      const s = typeof e.source === 'object' ? e.source.id : e.source
      const t = typeof e.target === 'object' ? e.target.id : e.target
      return (s === d.id || t === d.id) ? 1 : 0.1
    })

    const rect = svgEl.getBoundingClientRect()
    const pl = d.dataProperties?.join(', ') || ''
    tooltip.value = {
      x: event.clientX - rect.left + 12,
      y: event.clientY - rect.top - 10,
      text: d.name + (pl ? `  [${pl}]` : ''),
      visible: true,
    }
  })

  node.on('mouseleave', () => {
    node.style('opacity', 1); link.style('opacity', 0.6); edgeLabel.style('opacity', 1)
    tooltip.value.visible = false
  })

  node.on('click', function (_event: MouseEvent, d: any) {
    router.push(`/ontologies/${props.ontologyId}/classes/${d.id}`)
  })

  // Force simulation
  simulation = d3.forceSimulation(d3Nodes)
    .force('link', d3.forceLink(d3Edges).id((d: any) => d.id).distance(160))
    .force('charge', d3.forceManyBody().strength(-600))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(50))
    .on('tick', () => {
      link
        .attr('x1', (d: any) => d.source.x).attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x).attr('y2', (d: any) => d.target.y)
      edgeLabel
        .attr('x', (d: any) => (d.source.x + d.target.x) / 2)
        .attr('y', (d: any) => (d.source.y + d.target.y) / 2)
      node.attr('transform', (d: any) => `translate(${d.x},${d.y})`)
    })

  simulation.alpha(1).restart()
}
</script>

<template>
  <div class="graph-wrap">
    <!-- SVG is always in DOM -->
    <svg ref="svgRef" class="graph-svg"></svg>

    <!-- Overlay: loading -->
    <div v-if="loading" class="graph-overlay">
      <div class="loading">加载图中...</div>
    </div>

    <!-- Overlay: error -->
    <div v-else-if="error" class="graph-overlay">
      <div class="alert alert-error" style="margin: 20px;">{{ error }}</div>
    </div>

    <!-- Tooltip -->
    <div v-if="tooltip.visible" class="graph-tooltip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }">
      {{ tooltip.text }}
    </div>

    <!-- Legend -->
    <div v-if="!loading && !error" class="graph-legend">
      <span><span class="legend-line hierarchy"></span> 类层级</span>
      <span><span class="legend-line property"></span> 对象属性</span>
      <span class="legend-hint">滚轮缩放 · 拖拽节点 · 点击查看详情</span>
    </div>
  </div>
</template>

<style scoped>
.graph-wrap {
  position: relative;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: #fafbfc;
}
.graph-svg {
  display: block;
  width: 100%;
  min-height: 500px;
}
.graph-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(250, 251, 252, 0.85);
}
.graph-tooltip {
  position: absolute;
  background: #1e293b;
  color: #e2e8f0;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  z-index: 10;
}
.graph-legend {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-muted);
  border-top: 1px solid var(--border);
  display: flex;
  gap: 16px;
}
.legend-line {
  display: inline-block;
  width: 12px;
  height: 2px;
  vertical-align: middle;
  margin-right: 4px;
}
.legend-line.hierarchy { background: #6366f1; }
.legend-line.property { background: #f59e0b; }
.legend-hint { margin-left: auto; }
</style>
