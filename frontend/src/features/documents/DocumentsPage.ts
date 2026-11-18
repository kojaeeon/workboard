import { defineComponent } from 'vue'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api, act, confirm, success, error } from '../../shared/api'
import MarkdownEditor from './MarkdownEditor.vue'
import MarkdownPreview from './MarkdownPreview.vue'
import { headings } from './markdown'
import './DocumentsPage.css'
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

export default defineComponent({
  components: { MarkdownEditor, MarkdownPreview },
  props: {
    theme: { type: String, required: true },
    dark: { type: Boolean, required: true },
  },
  setup() {
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
      document,
      location,
      preview,
      editor,
      previewRoot,
      entries,
      refreshLibrary,
      open,
      openLocal,
      setMode,
      save,
      remove,
      register,
      unregister,
      jump,
      jumpEnd,
      basename,
      uploaded,
    }
  },
})
