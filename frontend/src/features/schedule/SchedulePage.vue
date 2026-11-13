<script setup lang="ts">
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
</script>
<template>
  <section class="page">
    <div class="eyebrow">PLAN YOUR DAY</div>
    <div class="page-title">일정</div>
    <p class="muted page-subtitle">
      날짜를 눌러 등록하고, 일정을 눌러 수정하세요. 시간은 한국 시간 기준입니다.
    </p>
    <div class="row items-center row-gap">
      <q-btn color="primary" label="일정 추가" icon="add" @click="editor()" /><q-btn
        flat
        label="새로고침"
        icon="refresh"
        @click="act(refresh)"
      />
      <div v-for="c in categories" :key="c.value" class="row items-center" style="gap: 4px">
        <q-icon name="circle" :style="{ color: c.color }" size="12px" /><span>{{ c.label }}</span>
      </div>
    </div>
    <q-card
      ><Calendar
        ref="calendar"
        class="schedule-calendar"
        :events="events"
        resource-path="/vendor/calendar"
        @date_click="editor(undefined, $event.start, $event.allDay)"
        @event_click="open($event.id)"
        @event_change="move"
        @period_change="changed"
    /></q-card>
    <q-expansion-item label="현재 기간의 일정 목록" icon="event_note"
      ><div v-if="!events.length" class="muted q-pa-md">표시할 일정이 없습니다.</div>
      <q-btn
        v-for="event in events"
        :key="event.id"
        flat
        no-caps
        class="full-width"
        align="left"
        :label="`${event.start.replace('T', ' ').slice(0, event.allDay ? 10 : 16)} · ${event.title}`"
        @click="open(event.id)"
    /></q-expansion-item>
    <q-dialog v-model="dialog"
      ><q-card style="width: 576px; max-width: 100%" class="column column-gap"
        ><div class="text-h6">{{ form.id ? '일정 수정' : '새 일정' }}</div>
        <q-input v-model="form.title" label="일정 제목" /><q-select
          v-model="form.category"
          :options="categories"
          emit-value
          map-options
          label="분류" /><q-checkbox v-model="form.allDay" label="종일" />
        <div class="row row-gap">
          <q-input v-model="form.startDate" label="시작 날짜" type="date" class="col" /><q-input
            v-model="form.endDate"
            label="종료 날짜"
            type="date"
            class="col"
          />
        </div>
        <div v-if="!form.allDay" class="row row-gap">
          <q-input v-model="form.startTime" label="시작 시간" type="time" class="col" /><q-input
            v-model="form.endTime"
            label="종료 시간"
            type="time"
            class="col"
          />
        </div>
        <div class="muted">종일 일정의 종료 날짜는 마지막 일정일입니다.</div>
        <q-input v-model="form.notes" label="일정 메모" type="textarea" />
        <div class="row justify-end row-gap">
          <q-btn
            v-if="form.id"
            flat
            label="삭제"
            color="negative"
            icon="delete"
            @click="remove"
          /><q-btn flat label="취소" @click="dialog = false" /><q-btn
            color="primary"
            label="저장"
            :loading="saving"
            @click="save"
          /></div></q-card
    ></q-dialog>
  </section>
</template>
