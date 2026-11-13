<script setup lang="ts">
import { computed } from 'vue'
import hljs from 'highlight.js'
import { act } from './api'
const props = defineProps<{ content: string; language?: string }>()
function copy() {
  return act(() => navigator.clipboard.writeText(props.content))
}
const html = computed(() =>
  props.language && hljs.getLanguage(props.language)
    ? hljs.highlight(props.content, { language: props.language }).value
    : props.content.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;'),
)
</script>
<template>
  <div class="code-display full-width">
    <div class="row justify-end">
      <q-btn flat round dense icon="content_copy" aria-label="코드 복사" @click="copy" />
    </div>
    <pre><code v-html="html"/></pre>
  </div>
</template>
<style scoped>
.code-display {
  position: relative;
  min-width: 0;
}
.code-display pre {
  margin: 0;
  overflow: auto;
  max-width: 100%;
  padding: 16px;
  background: #f6f8fa;
  border-radius: 8px;
  font: 14px/1.6 monospace;
}
.body--dark .code-display pre {
  background: #0d1117;
  color: #e6edf3;
}
</style>
