import { defineComponent } from 'vue'
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { Notify } from 'quasar'
import MindMapEditor from '../../MindMapEditor.js'
import '../../mindmap.css'
import { api, act, confirm, download, success } from '../../shared/api'
import { newGraph, type Graph, type NodeType } from './model'
import { importGraph, exportGraph } from './formats'
interface Entry {
  id: number
  title: string
  revision: number
}

export default defineComponent({
  components: { MindMapEditor },
  setup() {
    const graph = ref<Graph>(),
      types = ref<NodeType[]>([]),
      library = ref<Entry[]>([]),
      editor = ref<any>(),
      title = ref('고객 관리 서비스'),
      status = ref('새 맵 · SQLite에 저장하세요'),
      busy = ref(false)
    const state = reactive({
      id: null as number | null,
      revision: null as number | null,
      title: '고객 관리 서비스',
    })
    const importing = ref(false),
      exporting = ref(false),
      inputFormat = ref('dot'),
      outputFormat = ref('dot'),
      source = ref(''),
      output = ref(''),
      working = ref(false),
      snapshot = ref<Graph>()
    const formats = [
      { value: 'dot', label: 'DOT' },
      { value: 'flowchart', label: 'Mermaid flowchart' },
      { value: 'mindmap', label: 'Mermaid mindmap' },
      { value: 'json', label: 'KJA JSON' },
      { value: 'png', label: 'PNG 이미지' },
    ]
    async function refresh() {
      library.value = await api('/mindmaps')
    }
    function showImport() {
      source.value = ''
      inputFormat.value = 'dot'
      importing.value = true
    }
    async function discard() {
      return (
        (!editor.value?.getSnapshot().dirty && title.value === state.title) ||
        (await confirm('저장하지 않은 변경을 버리고 다른 맵을 여시겠습니까?'))
      )
    }
    async function replace(value: Graph, name: string, item?: Entry) {
      if (!(await discard())) return
      state.id = item?.id ?? null
      state.revision = item?.revision ?? null
      state.title = name
      title.value = name
      graph.value = value
      await nextTick()
      editor.value?.loadGraphFromProps()
      status.value = item ? `저장된 맵 #${item.id}` : '새 맵 · SQLite에 저장하세요'
    }
    async function example() {
      await act(async () => replace(await api<Graph>('/mindmaps/example'), '고객 관리 서비스'))
    }
    async function open(item: Entry) {
      await act(async () => {
        const saved = await api<Entry & { graph: Graph }>(`/mindmaps/${item.id}`)
        await replace(saved.graph, saved.title, saved)
      })
    }
    async function save() {
      if (busy.value) return
      busy.value = true
      try {
        await act(async () => {
          const value = editor.value?.getSnapshot().graph
          if (!value) throw new Error('마인드맵 로딩이 끝난 뒤 다시 저장하세요.')
          const result = await api<{ id: number; revision: number }>(
            state.id ? `/mindmaps/${state.id}` : '/mindmaps',
            state.id ? 'PUT' : 'POST',
            { title: title.value, graph: value, revision: state.revision },
          )
          Object.assign(state, result, { title: title.value })
          editor.value.markSaved()
          status.value = `SQLite 저장 완료 · #${state.id} · v${state.revision}`
          await refresh()
          success('마인드맵을 저장했습니다.')
        })
      } finally {
        busy.value = false
      }
    }
    async function remove(item: Entry) {
      if (!(await confirm(`“${item.title}” 맵을 삭제하시겠습니까?`))) return
      await act(async () => {
        await api(`/mindmaps/${item.id}?revision=${item.revision}`, 'DELETE')
        if (state.id === item.id) {
          state.id = null
          state.revision = null
          status.value = '저장본 삭제됨 · 현재 편집 내용은 새 맵으로 저장 가능'
        }
        await refresh()
      })
    }
    async function saveType(type: NodeType) {
      await act(async () => {
        await api('/mindmaps/types', 'PUT', type)
        types.value = await api('/mindmaps/types')
        editor.value.updateTypes(types.value)
        success('유형 기본 스타일을 저장했습니다.')
      })
    }
    async function upload(event: Event) {
      const input = event.target as HTMLInputElement,
        file = input.files?.[0]
      if (!file) return
      await act(async () => {
        if (file.size > 2 * 1024 * 1024) throw new Error('2MB 이하 파일만 지원합니다.')
        source.value = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
        const extension = file.name.split('.').at(-1)?.toLowerCase()
        inputFormat.value = ['dot', 'gv'].includes(extension || '')
          ? 'dot'
          : extension === 'json'
            ? 'json'
            : 'mermaid'
      })
      input.value = ''
    }
    async function applyImport() {
      working.value = true
      try {
        await act(async () => {
          const result = await importGraph(source.value, inputFormat.value)
          if (!(await discard())) return
          state.id = null
          state.revision = null
          state.title = '가져온 마인드맵'
          title.value = state.title
          graph.value = result.graph
          await nextTick()
          editor.value.loadGraphFromProps()
          status.value = '가져오기 완료 · 저장하면 새 맵으로 등록'
          importing.value = false
          result.warnings.forEach((message) => Notify.create({ message, timeout: 8000 }))
        })
      } finally {
        working.value = false
      }
    }
    async function showExport() {
      await act(async () => {
        snapshot.value = editor.value?.getSnapshot().graph
        if (!snapshot.value) throw new Error('마인드맵 로딩이 끝난 뒤 다시 시도하세요.')
        outputFormat.value = 'dot'
        output.value = await exportGraph(snapshot.value, 'dot')
        exporting.value = true
      })
    }
    watch(outputFormat, async (value) => {
      if (value !== 'png' && snapshot.value)
        await act(async () => {
          output.value = await exportGraph(snapshot.value!, value)
        })
    })
    async function exportFile() {
      working.value = true
      try {
        await act(async () => {
          if (outputFormat.value !== 'png') {
            download(
              output.value,
              `mindmap.${outputFormat.value === 'dot' ? 'dot' : outputFormat.value === 'json' ? 'json' : 'mmd'}`,
            )
            return
          }
          const filename =
            (title.value || 'mindmap')
              .replace(/[<>:"/\\|?*\x00-\x1f]/g, '_')
              .replace(/^[ .]+|[ .]+$/g, '') || 'mindmap'
          const result = await editor.value.exportPng(filename + '.png')
          if (!result?.ok) throw new Error(result?.error || 'PNG 내보내기에 실패했습니다.')
        })
      } finally {
        working.value = false
      }
    }
    async function copy() {
      await act(() => navigator.clipboard.writeText(output.value))
    }
    onMounted(() =>
      act(async () => {
        const [example, nodeTypes] = await Promise.all([
          api<Graph>('/mindmaps/example'),
          api<NodeType[]>('/mindmaps/types'),
        ])
        types.value = nodeTypes
        graph.value = example
        await refresh()
      }),
    )

    return {
      Notify,
      graph,
      types,
      library,
      editor,
      title,
      status,
      busy,
      importing,
      exporting,
      inputFormat,
      outputFormat,
      source,
      output,
      working,
      formats,
      newGraph,
      showImport,
      replace,
      example,
      open,
      save,
      remove,
      saveType,
      upload,
      applyImport,
      showExport,
      exportFile,
      copy,
    }
  },
})
