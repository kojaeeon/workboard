import { defineComponent } from 'vue'
import { onMounted, reactive, ref } from 'vue'
import { api, act, confirm } from '../../shared/api'
import CommonGrid, { type GridColumn } from '../../shared/CommonGrid.vue'
import './WorkPage.css'
interface WorkItem {
  id: number
  title: string
  status: string
  notes: string
}

export default defineComponent({
  components: { CommonGrid },
  setup() {
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

    return {
      items,
      dialog,
      loading,
      busy,
      form,
      states,
      wiql,
      columns,
      rows,
      status,
      detail,
      grid,
      edit,
      save,
      remove,
      query,
    }
  },
})
