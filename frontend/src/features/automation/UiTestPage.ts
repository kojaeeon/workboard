import { defineComponent } from 'vue'
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { api, act, error } from '../../shared/api'
import MarkdownEditor from '../documents/MarkdownEditor.vue'
import CodeBlock from '../../shared/CodeBlock.vue'
import './UiTestPage.css'
interface Source {
  key: string
  label: string
  location: string
}
interface Step {
  action: string
  label: string
  name: string
  selector: string
  id: string
  text: string
  url: string
}
interface Browser {
  browser: string
  executable_path: string | null
  viewport: { width: number; height: number }
  base_url: string
  timeout: number
  headless: boolean
}
interface Validation {
  title: string
  steps: Step[]
  browser: Browser
}
interface Result {
  number: number
  action: string
  target: string
  status: string
  duration_ms: number | string
  strategy: string
  error: string
}
interface Report {
  id: string
  title: string
  status: string
  error: string
  steps: Result[]
  running: boolean
  files: string[]
  output: string
}

export default defineComponent({
  components: { MarkdownEditor, CodeBlock },
  props: {
    dark: { type: Boolean, required: true },
  },
  setup() {
    const sources = ref<Source[]>([]),
      selected = ref('example:ui-test-smoke.md'),
      browserSelected = ref<string | null>(null),
      source = ref(''),
      browserMarkdown = ref(''),
      browserMessage = ref('시나리오의 browser 설정을 사용합니다.'),
      browserError = ref(false),
      browserPreview = ref<Browser>(),
      title = ref(''),
      baseUrl = ref('http://127.0.0.1:8080'),
      headed = ref(true),
      message = ref('시나리오를 확인하고 실행하세요.'),
      rows = ref<Result[]>([]),
      running = ref(false),
      stopping = ref(false),
      starting = ref(false),
      report = ref<Report>(),
      files = ref<string[]>([]),
      output = ref(''),
      refreshing = ref(false)
    const clientId = crypto.randomUUID()
    const disabled = computed(() => running.value || starting.value)
    const labels: Record<string, string> = {
      pending: '대기',
      starting: '준비',
      running: '실행 중',
      passed: '성공',
      failed: '실패',
      skipped: '건너뜀',
      cancelled: '중지',
    }
    const columns = [
      { name: 'number', label: '#', field: 'number', align: 'left' as const },
      { name: 'action', label: '동작', field: 'action', align: 'left' as const },
      { name: 'target', label: '대상', field: 'target', align: 'left' as const },
      {
        name: 'status',
        label: '결과',
        field: (row: Result) => labels[row.status] || row.status,
        align: 'left' as const,
      },
      { name: 'duration_ms', label: 'ms', field: 'duration_ms', align: 'left' as const },
      { name: 'strategy', label: '요소 찾기', field: 'strategy', align: 'left' as const },
      { name: 'error', label: '오류', field: 'error', align: 'left' as const },
    ]
    const scenarioSources = computed(() =>
      sources.value.filter((s) => s.key !== 'example:browser.md'),
    )
    const image = computed(() =>
      files.value.includes('failure.png')
        ? 'failure.png'
        : files.value.includes('final.png')
          ? 'final.png'
          : '',
    )
    let timer: ReturnType<typeof setTimeout> | undefined,
      disposed = false,
      sourceVersion = 0,
      browserVersion = 0
    async function refresh() {
      refreshing.value = true
      try {
        await act(async () => {
          const result = await api<{ sources: Source[]; warnings: string[] }>('/ui-tests/sources')
          sources.value = result.sources
          result.warnings.forEach((w) => error(new Error(w)))
        })
      } finally {
        refreshing.value = false
      }
    }
    async function validate(sync = false) {
      try {
        const result = await api<Validation>('/ui-tests/validate', 'POST', {
          markdown: source.value,
          browserMarkdown: browserError.value ? undefined : browserMarkdown.value,
        })
        title.value = result.title
        rows.value = result.steps.map((step, i) => ({
          number: i + 1,
          action: step.action,
          target: step.label || step.name || step.selector || step.id || step.text || step.url,
          status: 'pending',
          duration_ms: '',
          strategy: '',
          error: '',
        }))
        message.value = `${result.steps.length}단계 · 프론트매터 검증 완료`
        if (sync) {
          browserPreview.value = result.browser
          baseUrl.value = result.browser.base_url
          headed.value = !result.browser.headless
        }
        return result
      } catch (e) {
        rows.value = []
        message.value = e instanceof Error ? e.message : String(e)
        return null
      }
    }
    async function load(key: string) {
      selected.value = key
      const version = ++sourceVersion
      await act(async () => {
        const result = await api<{ content: string }>(
          '/ui-tests/source?key=' + encodeURIComponent(key),
        )
        if (version !== sourceVersion) return
        source.value = result.content
        await validate(true)
      })
    }
    async function applyBrowser(content: string) {
      try {
        const result = await api<Browser>('/ui-tests/browser', 'POST', {
          markdown: source.value,
          browserMarkdown: content,
        })
        browserMarkdown.value = content
        browserError.value = false
        browserPreview.value = result
        baseUrl.value = result.base_url
        headed.value = !result.headless
        browserMessage.value = content
          ? '브라우저 MD 적용 완료 · 실행 시 이 설정을 사용합니다.'
          : '시나리오의 browser 설정을 사용합니다.'
      } catch (e) {
        browserError.value = true
        browserMessage.value = '브라우저 MD 오류: ' + (e instanceof Error ? e.message : String(e))
        error(e)
      }
    }
    async function loadBrowser(key: string | null) {
      browserSelected.value = key
      const version = ++browserVersion
      if (!key) {
        browserMarkdown.value = ''
        browserError.value = false
        browserMessage.value = '시나리오의 browser 설정을 사용합니다.'
        await validate(true)
        return
      }
      try {
        const result = await api<{ content: string }>(
          '/ui-tests/source?key=' + encodeURIComponent(key),
        )
        if (version === browserVersion) await applyBrowser(result.content)
      } catch (e) {
        browserError.value = true
        browserMessage.value = '브라우저 MD 오류: ' + (e instanceof Error ? e.message : String(e))
        error(e)
      }
    }
    async function upload(event: Event, browser = false) {
      const input = event.target as HTMLInputElement,
        file = input.files?.[0]
      if (!file || disabled.value) return
      try {
        if (file.size > 2097152) throw new Error('2MB 이하 파일만 지원합니다.')
        const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
        if (browser) {
          browserSelected.value = null
          await applyBrowser(text)
        } else {
          selected.value = ''
          source.value = text
          await validate(true)
        }
      } catch (e) {
        if (browser) {
          browserError.value = true
          browserMessage.value = '브라우저 MD 오류: ' + String(e)
        }
        error(e)
      } finally {
        input.value = ''
      }
    }
    function applyReport(value: Report) {
      report.value = value
      running.value = value.running
      rows.value = value.steps
      output.value = value.output
      const passed = value.steps.filter((s) => s.status === 'passed').length
      message.value =
        stopping.value && value.running
          ? '중지 요청 중 · 브라우저를 정리하고 있습니다.'
          : `${labels[value.status] || value.status} · ${passed}/${value.steps.length} 성공${value.error ? '\n' + value.error : ''}`
      files.value = value.running ? [] : value.files
      if (!value.running) stopping.value = false
    }
    async function poll() {
      if (disposed || !report.value) return
      try {
        applyReport(await api<Report>(`/ui-tests/runs/${report.value.id}`))
      } catch (e) {
        message.value = '실행 상태를 확인하지 못했습니다. 연결 후 다시 확인합니다.'
      } finally {
        if (!disposed && running.value) timer = setTimeout(poll, 500)
      }
    }
    async function start() {
      if (disabled.value || browserError.value) return
      starting.value = true
      try {
        if (!(await validate())) return
        files.value = []
        report.value = undefined
        stopping.value = false
        await act(async () => {
          const value = await api<Report>('/ui-tests/runs', 'POST', {
            markdown: source.value,
            browserMarkdown: browserMarkdown.value,
            baseUrl: baseUrl.value,
            headed: headed.value,
            clientId,
          })
          applyReport(value)
          if (value.running) timer = setTimeout(poll, 500)
        })
      } finally {
        starting.value = false
      }
    }
    async function stop() {
      if (!report.value) return
      stopping.value = true
      message.value = '중지 요청 중 · 브라우저를 정리하고 있습니다.'
      try {
        await api(`/ui-tests/runs/${report.value!.id}/stop`, 'POST')
      } catch (e) {
        stopping.value = false
        error(e)
      }
    }
    function artifact(name: string, preview = false) {
      return `/api/ui-tests/runs/${report.value?.id}/files/${encodeURIComponent(name)}${preview ? '?preview=true' : ''}`
    }
    onMounted(async () => {
      await refresh()
      await load(selected.value)
    })
    onBeforeUnmount(() => {
      disposed = true
      if (timer) clearTimeout(timer)
    })

    return {
      sources,
      selected,
      browserSelected,
      source,
      browserMessage,
      browserError,
      browserPreview,
      title,
      baseUrl,
      headed,
      message,
      rows,
      running,
      stopping,
      starting,
      files,
      output,
      refreshing,
      disabled,
      columns,
      scenarioSources,
      image,
      refresh,
      validate,
      load,
      loadBrowser,
      upload,
      start,
      stop,
      artifact,
    }
  },
})
