import { defineComponent } from 'vue'
import { nextTick, reactive, ref } from 'vue'
import Calendar from '../../Calendar.js'
import { api, act, confirm, success } from '../../shared/api'
interface CalendarEvent {
  id: string
  title: string
  start: string
  end: string
  allDay: boolean
  extendedProps: { category: string; notes: string }
}
interface StoredEvent {
  id: number
  title: string
  start: string
  end: string
  all_day: number
  category: string
  notes: string
}

export default defineComponent({
  components: { Calendar },
  setup() {
    const categories = [
      { value: 'work', label: '업무', color: '#51786b' },
      { value: 'personal', label: '개인', color: '#647eae' },
      { value: 'meeting', label: '회의', color: '#9b78ab' },
      { value: 'deadline', label: '마감', color: '#be6654' },
    ]
    const events = ref<CalendarEvent[]>([]),
      calendar = ref<any>(),
      dialog = ref(false),
      saving = ref(false)
    const period = reactive({ start: '', end: '' })
    const form = reactive({
      id: null as number | null,
      title: '',
      category: 'work',
      allDay: true,
      startDate: '',
      endDate: '',
      startTime: '09:00',
      endTime: '10:00',
      notes: '',
    })
    function dayShift(date: string, days: number) {
      return new Date(Date.parse(date.slice(0, 10) + 'T00:00:00Z') + days * 86400000)
        .toISOString()
        .slice(0, 10)
    }
    function today() {
      return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())
    }
    async function refresh() {
      try {
        if (period.start)
          events.value = await api(
            `/schedule?start=${encodeURIComponent(period.start)}&end=${encodeURIComponent(period.end)}`,
          )
      } finally {
        await nextTick()
        calendar.value?.update_calendar()
      }
    }
    function editor(item?: StoredEvent, start?: string, allDay = true) {
      const begin = item?.start || start || today(),
        finish =
          item?.end ||
          (allDay ? dayShift(begin, 1) : new Date(Date.parse(begin) + 3600000).toISOString())
      if (item) allDay = !!item.all_day
      const local = (text: string) => {
        if (text.length === 10) return text
        return new Intl.DateTimeFormat('sv-SE', {
          timeZone: 'Asia/Seoul',
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false,
        })
          .format(new Date(text.includes('+') || text.endsWith('Z') ? text : text + '+09:00'))
          .replace(' ', 'T')
      }
      const b = local(begin),
        e = local(finish)
      Object.assign(form, {
        id: item?.id ?? null,
        title: item?.title || '',
        category: item?.category || 'work',
        allDay,
        startDate: b.slice(0, 10),
        endDate: allDay ? dayShift(finish, -1) : e.slice(0, 10),
        startTime: allDay ? '09:00' : b.slice(11, 16),
        endTime: allDay ? '10:00' : e.slice(11, 16),
        notes: item?.notes || '',
      })
      dialog.value = true
    }
    async function open(id: string) {
      await act(async () => editor(await api<StoredEvent>(`/schedule/${id}`)))
    }
    async function save() {
      saving.value = true
      try {
        await act(async () => {
          await api(form.id ? `/schedule/${form.id}` : '/schedule', form.id ? 'PUT' : 'POST', {
            title: form.title,
            category: form.category,
            allDay: form.allDay,
            notes: form.notes,
            start: form.allDay ? form.startDate : `${form.startDate}T${form.startTime}`,
            end: form.allDay ? dayShift(form.endDate, 1) : `${form.endDate}T${form.endTime}`,
          })
          await refresh()
          dialog.value = false
          success('일정을 저장했습니다.')
        })
      } finally {
        saving.value = false
      }
    }
    async function remove() {
      if (await confirm('이 일정을 삭제할까요?\n' + form.title, '삭제'))
        await act(async () => {
          await api(`/schedule/${form.id}`, 'DELETE')
          await refresh()
          dialog.value = false
          success('일정을 삭제했습니다.')
        })
    }
    async function move(event: { id: string; start: string; end: string; allDay: boolean }) {
      try {
        await act(() =>
          api(`/schedule/${event.id}`, 'PATCH', {
            start: event.start,
            end: event.end,
            allDay: event.allDay,
          }),
        )
      } finally {
        await act(refresh)
      }
    }
    async function changed(value: { start: string; end: string }) {
      Object.assign(period, value)
      await act(refresh)
    }

    return {
      act,
      categories,
      events,
      calendar,
      dialog,
      saving,
      form,
      refresh,
      editor,
      open,
      save,
      remove,
      move,
      changed,
    }
  },
})
