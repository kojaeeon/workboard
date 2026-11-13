<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api, act, confirm, success, error } from '../../shared/api'
import MarkdownEditor from './MarkdownEditor.vue'
import MarkdownPreview from './MarkdownPreview.vue'
import { headings } from './markdown'
defineProps<{ theme: string; dark: boolean }>()
interface Document {
  id: number
  title: string
  content: string
}
interface LocalFile {
  path: string
  title: string
  content: string
  revision: string
}
interface Folder {
  id: number
  path: string
  files: string[]
  error: string | null
}
const documents = ref<Document[]>([]),
  folders = ref<Folder[]>([]),
  folderPath = ref(''),
  library = ref(true),
  mode = ref('render'),
  indexOpen = ref(false),
  busy = ref(false)
const document = reactive({ id: null as number | null, title: '시작하기', content: '' }),
  local = ref<LocalFile | null>(null),
  location = ref('저장 위치: SQLite 문서'),
  preview = ref(''),
  editor = ref<InstanceType<typeof MarkdownEditor>>(),
  previewRoot = ref<HTMLElement>()
const entries = computed(() => headings(document.content))
let generation = 0
async function refreshLibrary() {
  const results = await Promise.all([
    api<Document[]>('/documents'),
    api<Folder[]>('/documents/folders'),
  ])
  documents.value = results[0]
  folders.value = results[1]
}
function open(item: Document | { title: string; content: string }) {
  generation++
  local.value = null
  Object.assign(document, {
    id: 'id' in item ? item.id : null,
    title: item.title,
    content: item.content.replace(/\r\n?/g, '\n'),
  })
  location.value = '저장 위치: SQLite 문서'
  preview.value = document.content
}
async function openLocal(path: string) {
  await act(async () => {
    const file = await api<LocalFile>('/documents/local?path=' + encodeURIComponent(path))
    open(file)
    local.value = file
    location.value = '저장 위치: ' + file.path
  })
}
function setMode(value: string) {
  mode.value = value
  if (value === 'render') preview.value = document.content
  else void nextTick(() => editor.value?.focus())
}
async function save() {
  if (busy.value) return
  busy.value = true
  const version = generation,
    snapshot = { ...document },
    file = local.value ? { ...local.value } : null
  try {
    await act(async () => {
      if (file) {
        const result = await api<LocalFile>('/documents/local', 'PUT', {
          path: file.path,
          revision: file.revision,
          content: snapshot.content,
        })
        if (version === generation) local.value = result
        success('원본 MD 파일을 저장했습니다.')
      } else {
        const result = await api<{ id: number }>(
          snapshot.id ? `/documents/${snapshot.id}` : '/documents',
          snapshot.id ? 'PUT' : 'POST',
          { title: snapshot.title, content: snapshot.content },
        )
        if (version === generation && !snapshot.id) document.id = result.id
        await refreshLibrary()
        success('문서를 저장했습니다.')
      }
    })
  } finally {
    busy.value = false
  }
}
async function remove() {
  if (document.id === null) return
  if (await confirm('이 문서를 삭제할까요?', '삭제'))
    await act(async () => {
      await api(`/documents/${document.id}`, 'DELETE')
      open({ title: '새 문서', content: '' })
      await refreshLibrary()
    })
}
async function register() {
  await act(async () => {
    await api('/documents/folders', 'POST', { path: folderPath.value })
    folderPath.value = ''
    await refreshLibrary()
  })
}
async function unregister(id: number) {
  await act(async () => {
    await api(`/documents/folders/${id}`, 'DELETE')
    await refreshLibrary()
  })
}
async function jump(ordinal: number, line: number) {
  indexOpen.value = false
  if (mode.value === 'text') {
    editor.value?.jump(line)
    return
  }
  preview.value = document.content
  await nextTick()
  const target = previewRoot.value?.querySelectorAll<HTMLElement>(
    '.nicegui-markdown h1,.nicegui-markdown h2,.nicegui-markdown h3,.nicegui-markdown h4,.nicegui-markdown h5,.nicegui-markdown h6',
  )[ordinal]
  if (target) {
    target.style.scrollMarginTop = '150px'
    target.tabIndex = -1
    target.scrollIntoView({ block: 'start' })
    target.focus({ preventScroll: true })
  }
}
function jumpEnd(end = false) {
  if (mode.value === 'text') editor.value?.jumpEnd(end)
  else {
    const target = end ? previewRoot.value?.lastElementChild : previewRoot.value
    target?.scrollIntoView({ block: end ? 'end' : 'start' })
  }
}
const basename = (path: string) => path.split(/[\\/]/).pop() || path
function uploaded(info: { xhr: XMLHttpRequest }) {
  open(JSON.parse(info.xhr.responseText))
  location.value = '저장 위치: SQLite 문서 (불러온 파일의 원본 경로는 제공되지 않습니다)'
}
onMounted(() =>
  act(async () => {
    await refreshLibrary()
    open(await api<{ title: string; content: string }>('/documents/sample'))
  }),
)
</script>
<template>
  <section class="page">
    <div class="eyebrow">YOUR PRIVATE STUDIO</div>
    <div class="page-title">Markdown Workspace</div>
    <p class="muted page-subtitle">문서와 다이어그램을 한 곳에서 관리하세요.</p>
    <div class="documents-layout">
      <q-card v-show="library" class="library column column-gap"
        ><div class="text-h6">문서</div>
        <div v-if="documents.length">
          <q-btn
            v-for="item in documents"
            :key="item.id"
            flat
            no-caps
            class="full-width"
            align="left"
            :label="item.title"
            @click="open(item)"
          />
        </div>
        <q-separator />
        <div class="text-h6">로컬 폴더</div>
        <q-input v-model="folderPath" label="폴더 경로" placeholder="C:\문서\Markdown" />
        <div class="row items-center">
          <q-btn
            label="폴더 등록"
            color="primary"
            icon="create_new_folder"
            @click="register"
          /><q-btn
            icon="refresh"
            flat
            round
            aria-label="폴더 목록 새로고침"
            @click="act(refreshLibrary)"
          />
        </div>
        <div v-if="!folders.length" class="muted text-caption">
          읽을 폴더 경로를 등록하세요. 등록 폴더 바로 아래의 MD 파일만 조회합니다.
        </div>
        <q-expansion-item
          v-for="folder in folders"
          :key="folder.id"
          :label="basename(folder.path)"
          icon="folder"
          class="full-width"
          ><div style="overflow-wrap: anywhere" class="text-caption">{{ folder.path }}</div>
          <q-btn flat dense label="등록 해제" icon="link_off" @click="unregister(folder.id)" />
          <div v-if="folder.error" class="text-warning text-caption">
            폴더를 읽을 수 없습니다: {{ folder.error }}
          </div>
          <div class="muted text-caption">MD {{ folder.files.length }}개</div>
          <q-btn
            v-for="path in folder.files"
            :key="path"
            flat
            no-caps
            icon="description"
            :label="basename(path)"
            align="left"
            class="full-width"
            @click="openLocal(path)"
        /></q-expansion-item>
        <q-uploader
          url="/api/documents/upload"
          field-name="file"
          label="MD 불러오기"
          auto-upload
          accept=".md,.markdown,text/markdown"
          :max-file-size="2097152"
          style="width: 100%; height: 115px"
          @uploaded="uploaded"
          @rejected="error(new Error('2MB 이하 파일을 선택하세요.'))"
          @failed="error(new Error('UTF-8 형식의 MD 파일이 필요합니다.'))"
        />
      </q-card>
      <div class="column column-gap grow min-w-0">
        <div class="row items-center row-gap">
          <q-input v-model="document.title" label="문서 제목" class="grow" /><q-btn
            color="primary"
            label="저장"
            icon="save"
            :loading="busy"
            @click="save"
          /><q-btn flat icon="delete" color="negative" aria-label="문서 삭제" @click="remove" />
        </div>
        <div class="text-caption muted" style="overflow-wrap: anywhere">{{ location }}</div>
        <div class="row items-center" style="gap: 8px">
          <q-btn
            flat
            round
            :icon="library ? 'menu_open' : 'menu'"
            :aria-label="library ? '왼쪽 목록 접기' : '왼쪽 목록 펼치기'"
            :aria-expanded="library"
            @click="library = !library"
          /><q-btn
            color="primary"
            round
            icon="code"
            aria-label="텍스트 모드"
            :flat="mode !== 'text'"
            :unelevated="mode === 'text'"
            @click="setMode('text')"
          /><q-btn
            color="primary"
            round
            icon="visibility"
            aria-label="렌더 모드"
            :flat="mode !== 'render'"
            :unelevated="mode === 'render'"
            @click="setMode('render')"
          /><q-btn
            flat
            round
            icon="format_list_bulleted"
            aria-label="문서 목차 열기"
            @click="indexOpen = true"
          /><q-space /><q-btn
            flat
            round
            icon="refresh"
            aria-label="미리보기 새로고침"
            @click="preview = document.content"
          />
        </div>
        <q-card v-show="mode === 'text'" class="full-width min-w-0"
          ><div class="text-caption muted q-mb-md">
            Ctrl+F 찾기 · F3 다음 · Shift+F3 이전 · Esc 닫기
          </div>
          <MarkdownEditor ref="editor" v-model="document.content" :dark="dark"
        /></q-card>
        <q-card v-show="mode === 'render'" class="full-width min-w-0" style="overflow: auto"
          ><div ref="previewRoot"><MarkdownPreview :content="preview" :theme="theme" /></div
        ></q-card>
      </div>
    </div>
    <div class="document-jump-widget column" role="navigation" aria-label="문서 처음과 끝으로 이동">
      <q-btn
        flat
        round
        icon="vertical_align_top"
        aria-label="문서 맨 위로 이동"
        @click="jumpEnd()"
      /><q-separator /><q-btn
        flat
        round
        icon="vertical_align_bottom"
        aria-label="문서 맨 아래로 이동"
        @click="jumpEnd(true)"
      />
    </div>
    <q-dialog v-model="indexOpen"
      ><q-card style="width: 576px; max-width: 100%"
        ><div class="row justify-between items-center">
          <div class="text-h6">문서 목차 · Index</div>
          <q-btn flat round icon="close" aria-label="목차 닫기" @click="indexOpen = false" />
        </div>
        <div class="column" style="max-height: 65vh; overflow: auto; gap: 4px">
          <div v-if="!entries.length">문서에 제목이 없습니다.</div>
          <q-btn
            v-for="(entry, i) in entries"
            :key="i"
            flat
            no-caps
            align="left"
            :label="`H${entry.level} · ${entry.label}`"
            :class="`toc-entry toc-level-${entry.level}`"
            :style="{ paddingLeft: `${12 + (entry.level - 1) * 16}px` }"
            @click="jump(i, entry.line)"
          /></div></q-card
    ></q-dialog>
  </section>
</template>
<style scoped>
.documents-layout {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}
.library {
  width: 256px;
  flex-shrink: 0;
}
@media (max-width: 1023px) {
  .documents-layout {
    flex-wrap: wrap;
  }
  .library {
    width: 100%;
  }
}
</style>
