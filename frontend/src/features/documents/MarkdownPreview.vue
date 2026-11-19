<script setup lang="ts">
import { computed } from 'vue'
import 'highlight.js/styles/github.css'
import MermaidDiagram from './MermaidDiagram.vue'
import { blocks } from './markdown'
import { renderMarkdown as html, renderCode as code } from './markdownRender'
import { error } from '../../shared/api'
const props = defineProps<{ content: string; theme: string }>()
const segments = computed(() => blocks(props.content))
async function copy(source: string) {
  try {
    await navigator.clipboard.writeText(source)
  } catch (e) {
    error(e)
  }
}
</script>
<template>
  <div class="column full-width" style="gap: 16px">
    <template v-for="(block, i) in segments" :key="i">
      <div v-if="block.kind === 'markdown'" class="nicegui-markdown" v-html="html(block.source)" />
      <MermaidDiagram v-else-if="block.kind === 'mermaid'" :source="block.source" :theme="theme" />
      <q-expansion-item
        v-else-if="block.kind === 'frontmatter'"
        label="Frontmatter"
        icon="data_object"
        class="full-width"
        ><div class="row justify-between items-center">
          <span class="muted text-caption">yaml</span
          ><q-btn
            flat
            round
            dense
            icon="content_copy"
            aria-label="코드 복사"
            @click="copy(block.source)"
          />
        </div>
        <pre class="code-block"><code v-html="code(block.source,'yaml')"/></pre>
      </q-expansion-item>
      <div v-else class="column full-width">
        <div class="row justify-between items-center">
          <span class="muted text-caption">{{ block.language || 'text' }}</span
          ><q-btn
            flat
            round
            dense
            icon="content_copy"
            aria-label="코드 복사"
            @click="copy(block.source)"
          />
        </div>
        <pre class="code-block"><code v-html="code(block.source,block.language)"/></pre>
      </div>
    </template>
  </div>
</template>
<style>
.code-block {
  width: 100%;
  overflow: auto;
  border-radius: 8px;
  padding: 16px;
  background: #f6f8fa;
  margin: 0;
  font: 14px/1.5 monospace;
}
.body--dark .code-block {
  background: #0d1117;
  color: #e6edf3;
}
.body--dark .hljs-keyword {
  color: #ff7b72;
}
.body--dark .hljs-string {
  color: #a5d6ff;
}
</style>
