<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, act, confirm } from '../../shared/api'
import CommonGrid, { type GridColumn } from '../../shared/CommonGrid.vue'
interface WorkItem {
  id: number
  title: string
  status: string
  notes: string
}
const items = ref<WorkItem[]>([]),
  dialog = ref(false),
  loading = ref(false),
  busy = ref(false)
const form = reactive({ id: null as number | null, title: '', status: 'todo', notes: '' })
const states = [
  { label: '할 일', value: 'todo' },
  { label: '진행 중', value: 'doing' },
  { label: '완료', value: 'done' },
]
const wiql = ref(
  'SELECT [System.Id], [System.Title] FROM WorkItems WHERE [System.TeamProject] = @project ORDER BY [System.ChangedDate] DESC',
)
const columns = ref<GridColumn[]>([]),
  rows = ref<Record<string, unknown>[]>([]),
  status = ref('실행 전 · 조회 결과가 없습니다.'),
  detail = ref<Record<string, unknown> | null>(null),
  grid = ref<InstanceType<typeof CommonGrid>>()
async function refresh() {
  items.value = await api('/work')
}
function edit(item?: WorkItem) {
  Object.assign(form, item || { id: null, title: '', status: 'todo', notes: '' })
  dialog.value = true
}
async function save() {
  busy.value = true
  try {
    await act(async () => {
      await api(form.id ? `/work/${form.id}` : '/work', form.id ? 'PUT' : 'POST', {
        title: form.title,
        status: form.status,
        notes: form.notes,
      })
      dialog.value = false
      await refresh()
    })
  } finally {
    busy.value = false
  }
}
async function remove(item: WorkItem) {
  if (await confirm('이 작업을 삭제할까요?', '삭제'))
    await act(async () => {
      await api(`/work/${item.id}`, 'DELETE')
      await refresh()
    })
}
async function query() {
  loading.value = true
  try {
    await act(async () => {
      const result = await api<{
        columns: GridColumn[]
        rows: Record<string, unknown>[]
        total: number
      }>('/ado/wiql', 'POST', { query: wiql.value })
      grid.value?.reset()
      columns.value = result.columns
      rows.value = result.rows
      status.value = `${result.total}건 중 ${rows.value.length}건 표시 (최대 200건)`
    })
  } finally {
    loading.value = false
  }
}
onMounted(() => act(refresh))
</script>
<template>
  <section class="page">
    <div class="eyebrow">MAKE ROOM FOR GREAT WORK</div>
    <div class="page-title">Work</div>
    <p class="muted page-subtitle">아이디어에서 완성까지, 중요한 작업에 집중하세요.</p>
    <q-expansion-item label="ADO · WIQL 조회" icon="cloud"
      ><div class="column" style="gap: 16px; padding: 16px">
        <div class="row justify-end">
          <q-btn
            color="primary"
            label="WIQL 실행"
            icon="play_arrow"
            :loading="loading"
            @click="query"
          />
        </div>
        <span>환경변수의 ADO 설정으로 조회합니다. PAT는 화면과 DB에 저장하지 않습니다.</span
        ><q-input v-model="wiql" label="WIQL" type="textarea" outlined /><CommonGrid
          ref="grid"
          :columns="columns"
          :rows="rows"
          @detail="detail = $event"
        />
        <div class="text-caption">
          컬럼 제목에서 정렬·필터를 사용할 수 있습니다. 행을 더블클릭하면 상세 내용을 표시합니다.
        </div>
        <div>{{ status }}</div>
      </div></q-expansion-item
    >
    <div><q-btn color="primary" label="작업 추가" icon="add" @click="edit()" /></div>
    <div class="work-board">
      <q-card v-for="state in states" :key="state.value"
        ><div class="text-h6">{{ state.label }}</div>
        <div v-if="!items.some((i) => i.status === state.value)" class="muted">
          등록된 작업이 없습니다.
        </div>
        <div
          v-for="item in items.filter((i) => i.status === state.value)"
          :key="item.id"
          class="column q-mt-md"
        >
          <q-btn flat no-caps :label="item.title" class="full-width" @click="edit(item)" />
          <div class="text-body2" style="white-space: pre-wrap">{{ item.notes }}</div>
          <q-btn
            icon="delete"
            flat
            dense
            color="negative"
            class="self-start"
            aria-label="작업 삭제"
            @click="remove(item)"
          /></div
      ></q-card>
    </div>
    <q-dialog v-model="dialog"
      ><q-card style="width: 576px; max-width: 100%" class="column column-gap"
        ><div class="text-h6">{{ form.id ? '작업 수정' : '새 작업' }}</div>
        <q-input v-model="form.title" label="제목" /><q-select
          v-model="form.status"
          :options="states"
          label="상태"
          emit-value
          map-options /><q-input v-model="form.notes" type="textarea" label="메모" />
        <div class="row row-gap">
          <q-btn flat label="취소" @click="dialog = false" /><q-btn
            color="primary"
            label="저장"
            :loading="busy"
            @click="save"
          /></div></q-card
    ></q-dialog>
    <q-dialog :model-value="!!detail" @update:model-value="detail = null"
      ><q-card style="width: 768px; max-width: 100%" class="column column-gap"
        ><div class="text-h6">작업 항목 #{{ detail?.['System.Id'] }}</div>
        <div v-for="column in columns" :key="column.key">
          <strong>{{ column.label }}</strong>
          <div style="white-space: pre-wrap; overflow-wrap: anywhere">
            {{ detail?.[column.key] ?? '' }}
          </div>
        </div>
        <q-btn color="primary" label="닫기" @click="detail = null" /></q-card
    ></q-dialog>
  </section>
</template>
<style scoped>
.work-board {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.work-board > .q-card {
  width: 33.333%;
}
@media (max-width: 1023px) {
  .work-board {
    flex-wrap: wrap;
  }
  .work-board > .q-card {
    width: 100%;
  }
}
</style>
