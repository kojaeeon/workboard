<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { EditorView, keymap } from '@codemirror/view'
import { Compartment, EditorState } from '@codemirror/state'
import { markdown } from '@codemirror/lang-markdown'
import { search, searchKeymap } from '@codemirror/search'
import { vscodeLight, vscodeDark } from '@uiw/codemirror-theme-vscode'
import searchOverview from './searchOverview.js'
const props = defineProps<{
  modelValue: string
  dark: boolean
  height?: string
  disabled?: boolean
  ariaLabel?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const root = ref<HTMLElement>(),
  theme = new Compartment(),
  editable = new Compartment()
let view: EditorView | undefined
onMounted(() => {
  view = new EditorView({
    parent: root.value,
    doc: props.modelValue,
    extensions: [
      basicSetup,
      markdown(),
      search({ top: true }),
      keymap.of(searchKeymap),
      EditorView.lineWrapping,
      theme.of(props.dark ? vscodeDark : vscodeLight),
      editable.of([EditorView.editable.of(!props.disabled), EditorState.readOnly.of(!!props.disabled)]),
      EditorView.theme({
        '&': { height: props.height || '65vh', minHeight: props.height || '480px' },
        '.cm-scroller': { overflow: 'auto' },
      }),
      EditorView.contentAttributes.of({ 'aria-label': props.ariaLabel || 'Markdown 편집기' }),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
      }),
    ],
  })
  searchOverview(view)
})
watch(
  () => props.modelValue,
  (value) => {
    if (view && view.state.doc.toString() !== value)
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
  },
)
watch(
  () => props.dark,
  (value) => view?.dispatch({ effects: theme.reconfigure(value ? vscodeDark : vscodeLight) }),
)
watch(
  () => props.disabled,
  (value) => view?.dispatch({ effects: editable.reconfigure([EditorView.editable.of(!value), EditorState.readOnly.of(!!value)]) }),
)
function jump(line: number) {
  if (!view) return
  const target = view.state.doc.line(Math.min(Math.max(1, line), view.state.doc.lines))
  view.dispatch({
    selection: { anchor: target.from },
    effects: EditorView.scrollIntoView(target.from, { y: 'start' }),
  })
  view.focus()
}
function jumpEnd(end = false) {
  if (!view) return
  view.dispatch({
    selection: { anchor: end ? view.state.doc.length : 0 },
    effects: EditorView.scrollIntoView(end ? view.state.doc.length : 0, {
      y: end ? 'end' : 'start',
    }),
  })
  view.focus()
}
defineExpose({ jump, jumpEnd, focus: () => view?.focus() })
onBeforeUnmount(() => view?.destroy())
</script>
<template><div ref="root" class="full-width" /></template>
