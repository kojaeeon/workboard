import { defineComponent } from 'vue'
import { computed, onMounted, ref, watch } from 'vue'
import { api, act, confirm, download, error, success } from '../../shared/api'
import MarkdownEditor from '../documents/MarkdownEditor.vue'
import CommonGrid from '../../shared/CommonGrid.vue'
import type { QueryResult } from './types'
import './DatabaseQueryPage.css'
interface Source {
  key: string
  label: string
  location: string
}
interface Profile {
  key: string
  location: string
  markdown: string
  revision: string | null
}

export default defineComponent({
  components: { MarkdownEditor, CommonGrid },
  props: {
    dark: { type: Boolean, required: true },
  },
  emits: ['generate', 'invalidate'],
  setup(_props, { emit }) {
    const sources = ref<Source[]>([]),
      selected = ref<string | null>('example:db-query-sqlite.md'),
      key = ref<string | null>(null),
      revision = ref<string | null>(null),
      location = ref(''),
      markdown = ref(''),
      saved = ref(''),
      busy = ref(''),
      message = ref('MD를 읽고 접속정보와 SQL을 확인하세요.'),
      result = ref<QueryResult>(),
      detail = ref<Record<string, unknown> | null>(null)
    const columns = computed(
      () =>
        result.value?.columns.map((c) => ({
          key: c.key,
          label: c.label,
          width: /comment|remarks/i.test(c.label) ? 360 : 180,
          type: [-6, 5, 4, -5, 6, 7, 8, 2, 3].includes(c.jdbcType) ? 'number' : 'text',
        })) || [],
    )
    watch(markdown, () => {
      if (result.value) emit('invalidate', result.value.id)
      result.value = undefined
      message.value = 'MD가 변경되었습니다. 설정을 확인하고 다시 조회하세요.'
    })
    async function refresh() {
      const listing = await api<{ sources: Source[]; warnings: string[] }>('/database/profiles')
      sources.value = listing.sources
      listing.warnings.forEach((w) => error(new Error(w)))
    }
    function apply(profile: Profile) {
      key.value = profile.key
      selected.value = profile.key
      revision.value = profile.revision
      markdown.value = profile.markdown
      saved.value = profile.markdown
      location.value = profile.location
      message.value = 'MD를 불러왔습니다. 설정 확인 또는 SQL 실행을 누르세요.'
    }
    async function load(value: string | null) {
      if (!value) return
      if (
        markdown.value !== saved.value &&
        !(await confirm('저장하지 않은 MD 변경을 버리고 다른 문서를 여시겠습니까?'))
      )
        return
      await act(async () =>
        apply(await api<Profile>('/database/profile?key=' + encodeURIComponent(value))),
      )
    }
    async function upload(event: Event) {
      const input = event.target as HTMLInputElement,
        file = input.files?.[0]
      if (!file) return
      try {
        if (file.size > 2097152) throw new Error('2MB 이하 MD 파일만 지원합니다.')
        const content = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
        if (
          markdown.value !== saved.value &&
          !(await confirm('저장하지 않은 MD 변경을 버리고 파일을 불러오시겠습니까?'))
        )
          return
        key.value = null
        selected.value = null
        revision.value = null
        markdown.value = content
        saved.value = content
        location.value = '불러온 파일 · 저장하면 SQLite 문서로 등록'
        message.value = 'MD를 불러왔습니다.'
      } catch (e) {
        error(e)
      } finally {
        input.value = ''
      }
    }
    async function save() {
      if (busy.value) return
      busy.value = 'save'
      try {
        await act(async () => {
          const profile = await api<Profile>('/database/profile', 'POST', {
            key: key.value,
            revision: revision.value,
            markdown: markdown.value,
          })
          apply(profile)
          await refresh()
          success('DB 조회 MD를 저장했습니다.')
        })
      } finally {
        busy.value = ''
      }
    }
    async function execute(kind: 'validate' | 'connect' | 'query') {
      if (busy.value) return
      busy.value = kind
      try {
        if (kind === 'query') {
          if (result.value) emit('invalidate', result.value.id)
          result.value = undefined
          result.value = await api<QueryResult>('/database/query', 'POST', {
            markdown: markdown.value,
          })
          message.value = `${result.value.rows.length}건 · ${result.value.elapsedMs}ms · ${result.value.databaseType.toUpperCase()}`
        } else if (kind === 'connect') {
          const connection = await api<{ product: string; version: string }>(
            '/database/connect',
            'POST',
            { markdown: markdown.value },
          )
          message.value = `접속 성공 · ${connection.product} ${connection.version}`
          success('DB 접속을 확인했습니다.')
        } else {
          const profile = await api<{
            title: string
            databaseType: string
            maxRows: number
            timeoutSeconds: number
          }>('/database/validate', 'POST', { markdown: markdown.value })
          message.value = `설정 확인 완료 · ${profile.databaseType.toUpperCase()} · 최대 ${profile.maxRows}행 · ${profile.timeoutSeconds}초`
        }
      } catch (e) {
        message.value = e instanceof Error ? e.message : String(e)
        error(e)
      } finally {
        busy.value = ''
      }
    }
    onMounted(() =>
      act(async () => {
        await refresh()
        await load(selected.value)
      }),
    )

    return {
      act,
      download,
      emit,
      sources,
      selected,
      key,
      location,
      markdown,
      busy,
      message,
      result,
      detail,
      columns,
      refresh,
      load,
      upload,
      save,
      execute,
    }
  },
})
