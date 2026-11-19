<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import fullscreen from './mermaidFullscreen.js'
import { download, error } from '../../shared/api'
import { renderMermaid } from './renderMermaid'
const props = defineProps<{ source: string; theme: string }>()
const root = ref<HTMLElement>(),
  failure = ref('')
let generation = 0,
  disposed = false
async function render() {
  const version = ++generation
  try {
    const svg = await renderMermaid(props.source, props.theme)
    if (version === generation && !disposed && root.value) {
      root.value.innerHTML = svg
      failure.value = ''
    }
  } catch (e) {
    if (version === generation && !disposed) {
      failure.value = String(e)
      if (root.value) root.value.innerHTML = ''
    }
  }
}
function open() {
  const svg = root.value?.querySelector('svg')
  if (svg) void fullscreen(svg)
}
function exportDiagram(format: 'svg' | 'html') {
  const svg = root.value?.querySelector('svg')?.outerHTML
  if (!svg) {
    error(new Error('다이어그램 렌더링 후 다시 출력해 주세요.'))
    return
  }
  download(
    format === 'svg'
      ? svg
      : `<!doctype html><html lang="ko"><meta charset="utf-8"><title>Mermaid</title><body>${svg}</body></html>`,
    `mermaid.${format}`,
    format === 'svg' ? 'image/svg+xml;charset=utf-8' : 'text/html;charset=utf-8',
  )
}
onMounted(render)
watch(() => [props.source, props.theme], render)
onBeforeUnmount(() => {
  disposed = true
  generation++
})
</script>
<template>
  <div class="column full-width" style="gap: 4px">
    <div class="row justify-end">
      <q-btn flat round dense icon="fullscreen" aria-label="Mermaid 전체화면" @click="open" /><q-btn
        flat
        round
        dense
        aria-label="SVG 출력"
        icon="image"
        @click="exportDiagram('svg')"
      ><q-tooltip>SVG 출력</q-tooltip></q-btn><q-btn
        flat
        round
        dense
        aria-label="HTML 출력"
        icon="html"
        @click="exportDiagram('html')"
      ><q-tooltip>HTML 출력</q-tooltip></q-btn>
    </div>
    <div v-if="failure" role="alert" class="text-negative" style="white-space: pre-wrap">
      {{ failure }}
    </div>
    <div ref="root" class="mermaid full-width" />
  </div>
</template>
