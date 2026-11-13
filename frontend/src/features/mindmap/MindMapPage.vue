<script setup lang="ts">
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
</script>
<template>
  <section class="column" style="gap: 12px">
    <div class="row items-center" style="gap: 12px">
      <q-icon name="account_tree" size="24px" /><q-input
        v-model="title"
        label="맵 제목"
        outlined
        dense
        style="width: 288px"
      /><q-btn color="primary" label="저장" icon="save" :loading="busy" @click="save" /><q-btn
        color="primary"
        outline
        label="가져오기"
        icon="upload"
        @click="showImport"
      /><q-btn color="primary" outline label="내보내기" icon="download" @click="showExport" /><span
        class="text-caption"
        style="opacity: 0.6"
        >{{ status }}</span
      >
    </div>
    <div class="row no-wrap items-start full-width" style="gap: 12px">
      <aside class="column" style="width: 208px; flex-shrink: 0; gap: 8px">
        <span class="text-caption" style="letter-spacing: 0.1em; opacity: 0.6"
          >MIND MAP LIBRARY</span
        ><q-btn
          color="primary"
          outline
          label="새 마인드맵"
          icon="add"
          @click="replace(newGraph(), '새 마인드맵')"
        /><q-btn color="primary" flat label="서비스 예시" icon="schema" @click="example" />
        <div v-for="item in library" :key="item.id" class="row no-wrap items-center">
          <q-btn
            flat
            no-caps
            align="left"
            :label="item.title"
            class="grow min-w-0"
            @click="open(item)"
          /><q-btn
            flat
            round
            dense
            icon="delete_outline"
            aria-label="저장된 맵 삭제"
            @click="remove(item)"
          />
        </div>
        <span v-if="!library.length" class="text-caption q-pa-sm" style="opacity: 0.5"
          >저장한 맵이 없습니다.</span
        >
      </aside>
      <MindMapEditor
        v-if="graph"
        ref="editor"
        :graph="graph"
        :types="types"
        resource-path="/vendor/mindmap"
        class="grow min-w-0"
        @save_request="save"
        @type_save="saveType"
        @load_error="Notify.create({ type: 'negative', message: '마인드맵 로딩 실패: ' + $event })"
      />
    </div>
    <q-dialog v-model="importing"
      ><q-card style="width: 850px; max-width: 100%" class="column column-gap"
        ><div class="text-h6">맵 가져오기</div>
        <q-select
          v-model="inputFormat"
          :options="[
            { value: 'dot', label: 'DOT' },
            { value: 'mermaid', label: 'Mermaid mindmap / flowchart' },
            { value: 'json', label: 'KJA JSON' },
          ]"
          emit-value
          map-options
          label="입력 형식" /><q-input
          v-model="source"
          label="소스 코드"
          type="textarea"
          outlined
          :input-style="{ minHeight: '240px' }" /><input
          type="file"
          accept=".dot,.gv,.mmd,.md,.txt,.json"
          aria-label="마인드맵 파일 불러오기"
          @change="upload" /><span class="text-caption" style="opacity: 0.6"
          >순환·다중 부모는 계층과 추가 관계로 변환합니다. 스크립트·클릭 동작은 실행하지
          않습니다.</span
        >
        <div class="row row-gap">
          <q-btn flat label="취소" @click="importing = false" /><q-btn
            color="primary"
            label="가져오기 적용"
            :loading="working"
            @click="applyImport"
          /></div></q-card
    ></q-dialog>
    <q-dialog v-model="exporting"
      ><q-card style="width: 850px; max-width: 100%" class="column column-gap"
        ><div class="text-h6">마인드맵 내보내기</div>
        <q-select
          v-model="outputFormat"
          :options="formats"
          emit-value
          map-options
          label="출력 형식" /><q-input
          v-if="outputFormat !== 'png'"
          v-model="output"
          readonly
          outlined
          label="출력 코드"
          type="textarea"
          :input-style="{ minHeight: '290px' }" /><span class="text-caption" style="opacity: 0.6">{{
          outputFormat === 'png'
            ? '현재 펼침 상태의 전체 맵을 PNG로 저장합니다. 화면 밖 노드·연결선도 포함합니다.'
            : 'KJA 메타데이터 주석으로 유형·스타일·관계를 보존합니다. Mermaid mindmap의 추가 관계는 다른 뷰어에 표시되지 않습니다.'
        }}</span>
        <div class="row row-gap">
          <q-btn
            v-if="outputFormat !== 'png'"
            color="primary"
            label="코드 복사"
            icon="content_copy"
            @click="copy"
          /><q-btn
            color="primary"
            label="다운로드"
            icon="download"
            :loading="working"
            @click="exportFile"
          /><q-btn flat label="닫기" @click="exporting = false" /></div></q-card
    ></q-dialog>
  </section>
</template>
