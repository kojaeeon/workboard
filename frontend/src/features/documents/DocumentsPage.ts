import { defineComponent } from 'vue'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api, act, confirm, success, error } from '../../shared/api'
import MarkdownEditor from './MarkdownEditor.vue'
import MarkdownPreview from './MarkdownPreview.vue'
import { headings } from './markdown'
import { exportMarkdownPdf } from './markdownPdf'
import './DocumentsPage.css'
interface DocumentSummary {
  id: number
  title: string
}
interface Document extends DocumentSummary {
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

export default defineComponent({
  components: { MarkdownEditor, MarkdownPreview },
  props: {
    theme: { type: String, required: true },
    dark: { type: Boolean, required: true },
  },
  setup() {
    const documents = ref<DocumentSummary[]>([]),
      folders = ref<Folder[]>([]),
      folderPath = ref(''),
      library = ref(true),
      mode = ref('render'),
      indexOpen = ref(false),
      busy = ref(false),
      pdfBusy = ref(false)
    const document = reactive({ id: null as number | null, title: '시작하기', content: '' }),
      local = ref<LocalFile | null>(null),
      location = ref('저장 위치: SQLite 문서'),
      preview = ref(''),
      editor = ref<InstanceType<typeof MarkdownEditor>>(),
      previewRoot = ref<HTMLElement>()
    const entries = computed(() => headings(document.content))
    let generation = 0
    let openRequest = 0
    let requestedStoredId: number | undefined
    const uploadRequests = new WeakMap<File, number>()
    async function refreshLibrary() {
      const results = await Promise.all([
        api<DocumentSummary[]>('/documents'),
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
    async function load<T>(path: string, apply: (item: T) => void, storedId?: number) {
      const request = ++openRequest
      requestedStoredId = storedId
      await act(async () => {
        try {
          const item = await api<T>(path)
          if (request === openRequest) apply(item)
        } catch (failure) {
          if (request === openRequest) throw failure
        }
      })
    }
    function openStored(id: number) {
      return load<Document>(`/documents/${id}`, open, id)
    }
    function openLocal(path: string) {
      return load<LocalFile>('/documents/local?path=' + encodeURIComponent(path), (file) => {
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
      const id = document.id
      if (id === null || busy.value) return
      if (await confirm('이 문서를 삭제할까요?', '삭제'))
        await act(async () => {
          await api(`/documents/${id}`, 'DELETE')
          if (requestedStoredId === id) {
            openRequest++
            requestedStoredId = undefined
          }
          if (document.id === id) open({ title: '새 문서', content: '' })
          await refreshLibrary()
        })
    }
    async function exportPdf() {
      if (pdfBusy.value) return
      pdfBusy.value = true
      try {
        await act(async () => {
          await exportMarkdownPdf(document.title, document.content)
          success('PDF 파일을 내려받았습니다.')
        })
      } finally {
        pdfBusy.value = false
      }
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
    function trackUpload(files: File[]) {
      requestedStoredId = undefined
      for (const file of files) uploadRequests.set(file, ++openRequest)
    }
    function uploaded(info: { files: File[]; xhr: XMLHttpRequest }) {
      if (uploadRequests.get(info.files[0]) !== openRequest) return
      open(JSON.parse(info.xhr.responseText))
      location.value = '저장 위치: SQLite 문서 (불러온 파일의 원본 경로는 제공되지 않습니다)'
    }
    onMounted(() => {
      void act(refreshLibrary)
      void load<{ title: string; content: string }>('/documents/sample', (item) => {
        if (document.title === '시작하기' && !document.content) open(item)
      })
    })

    return {
      act,
      error,
      documents,
      folders,
      folderPath,
      library,
      mode,
      indexOpen,
      busy,
      pdfBusy,
      document,
      location,
      preview,
      editor,
      previewRoot,
      entries,
      refreshLibrary,
      openStored,
      openLocal,
      setMode,
      save,
      exportPdf,
      remove,
      register,
      unregister,
      jump,
      jumpEnd,
      basename,
      trackUpload,
      uploaded,
    }
  },
})
