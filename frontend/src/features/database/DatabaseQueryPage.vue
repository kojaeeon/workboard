<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, act, confirm, download, error, success } from '../../shared/api'
import MarkdownEditor from '../documents/MarkdownEditor.vue'
import CommonGrid from '../../shared/CommonGrid.vue'
import type { QueryResult } from './types'
defineProps<{ dark: boolean }>()
const emit = defineEmits<{ generate: [id: string]; invalidate: [id: string] }>()
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
</script>
<template>
  <section class="page">
    <div class="eyebrow">QUERY · GENERATE</div>
    <div class="page-title">DB조회</div>
    <p class="muted page-subtitle">
      접속정보와 SQL을 MD로 관리하고, 실제 조회 결과로 코드를 생성하세요.
    </p>
    <q-card class="column column-gap full-width"
      ><div class="row items-center full-width row-gap">
        <q-select
          :model-value="selected"
          @update:model-value="load"
          :options="sources.map((s) => ({ label: s.label, value: s.key }))"
          label="DB 조회 프론트매터 MD"
          emit-value
          map-options
          outlined
          class="grow min-w-0"
          :disable="!!busy"
        /><q-btn
          flat
          round
          icon="refresh"
          aria-label="DB 조회 MD 목록 새로고침"
          :disable="!!busy"
          @click="act(refresh)"
        /><label class="profile-upload"
          ><q-icon name="upload" /> MD 불러오기<input
            type="file"
            accept=".md,.markdown"
            aria-label="DB 조회 MD 불러오기"
            :disabled="!!busy"
            @change="upload" /></label
        ><q-btn
          color="primary"
          label="MD 저장"
          icon="save"
          :loading="busy === 'save'"
          :disable="!!busy"
          @click="save"
        /><q-btn
          flat
          label="MD 다운로드"
          icon="download"
          :disable="!!busy"
          @click="download(markdown, 'db-query.md', 'text/markdown;charset=utf-8')"
        />
      </div>
      <div class="text-caption muted" style="overflow-wrap: anywhere">
        저장 위치: {{ location }}
      </div>
      <div class="text-body2 muted">
        connection · query.sql · query.parameters · codegen · workItem을 작성하세요. 상대 SQLite
        경로는 대시보드 실행 폴더 기준입니다.
      </div>
      <MarkdownEditor
        v-model="markdown"
        :dark="dark"
        height="560px"
        :disabled="!!busy"
        aria-label="DB 조회 MD 편집기"
      />
      <div class="row items-center full-width row-gap">
        <q-btn
          flat
          label="설정 확인"
          icon="checklist"
          :loading="busy === 'validate'"
          :disable="!!busy"
          @click="execute('validate')"
        /><q-btn
          outline
          color="primary"
          label="접속 확인"
          icon="cable"
          :loading="busy === 'connect'"
          :disable="!!busy"
          @click="execute('connect')"
        /><q-btn
          color="primary"
          label="SQL 실행"
          icon="play_arrow"
          :loading="busy === 'query'"
          :disable="!!busy"
          @click="execute('query')"
        /><q-space /><q-btn
          color="primary"
          label="code_gen으로 보내기"
          icon="code"
          :disable="!result || result.truncated || !!busy"
          @click="result && emit('generate', result.id)"
        />
      </div>
      <div role="status" style="white-space: pre-wrap">{{ message }}</div></q-card
    >
    <q-card v-if="result" class="column column-gap full-width"
      ><div class="row items-center row-gap">
        <div class="text-h6">조회 결과 · {{ result.title }}</div>
        <q-badge outline color="primary">{{
          result.codegen.mode === 'metadata' ? '컬럼 메타데이터' : 'SELECT 컬럼 구조'
        }}</q-badge>
      </div>
      <q-banner v-if="result.truncated" class="bg-warning text-black full-width"
        >행 수 또는 결과 크기 제한으로 일부 결과가 잘렸습니다. 이 결과로 코드를 생성할 수
        없습니다.</q-banner
      ><CommonGrid
        :columns="columns"
        :rows="result.rows"
        file-name="db_query.csv"
        @detail="detail = $event"
      />
      <div class="text-caption muted">
        조회 결과는 최대 20개·30분 동안 메모리에 보관합니다. 접속 비밀번호는 결과에 저장하지
        않습니다.
      </div></q-card
    >
    <q-dialog :model-value="!!detail" @update:model-value="detail = null"
      ><q-card class="column column-gap" style="width: 768px; max-width: 100%"
        ><div class="text-h6">조회 행 상세</div>
        <div v-for="column in columns" :key="column.key">
          <strong>{{ column.label }}</strong>
          <div style="white-space: pre-wrap; overflow-wrap: anywhere">
            {{ detail?.[column.key] ?? '' }}
          </div>
        </div>
        <q-btn label="닫기" color="primary" @click="detail = null" /></q-card
    ></q-dialog>
  </section>
</template>
<style scoped>
.profile-upload {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #b89a6060;
  padding: 10px 16px;
  border-radius: 8px;
  color: var(--q-primary);
  cursor: pointer;
}
.profile-upload input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: 100%;
  cursor: pointer;
}
.profile-upload:has(input:disabled) {
  pointer-events: none;
  opacity: 0.5;
}
</style>
