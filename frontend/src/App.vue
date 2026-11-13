<script setup lang="ts">
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { useQuasar } from 'quasar'
import { api, act } from './shared/api'
const DocumentsPage = defineAsyncComponent(() => import('./features/documents/DocumentsPage.vue'))
const WorkPage = defineAsyncComponent(() => import('./features/work/WorkPage.vue'))
const SchedulePage = defineAsyncComponent(() => import('./features/schedule/SchedulePage.vue'))
const MindMapPage = defineAsyncComponent(() => import('./features/mindmap/MindMapPage.vue'))
const UiTestPage = defineAsyncComponent(() => import('./features/automation/UiTestPage.vue'))
const CodeGenPage = defineAsyncComponent(() => import('./features/codegen/CodeGenPage.vue'))
const q = useQuasar()
const tab = ref('documents')
const settings = reactive({ app_theme: 'light', mermaid_theme: 'default' })
const ready = ref(false)
const tabs = [
  ['documents', 'MD 보기', 'description'],
  ['work', 'Work', 'task_alt'],
  ['schedule', '일정', 'calendar_month'],
  ['ui_test', 'UI_TEST', 'science'],
  ['codegen', 'code_gen', 'code'],
  ['mindmap', '마인드맵', 'account_tree'],
]
async function theme(key: 'app_theme' | 'mermaid_theme', value: string) {
  await act(async () => {
    await api(`/settings/${key}`, 'PUT', { value })
    settings[key] = value
    q.dark.set(settings.app_theme === 'dark')
  })
}
onMounted(() =>
  act(async () => {
    Object.assign(settings, await api('/settings'))
    q.dark.set(settings.app_theme === 'dark')
    ready.value = true
  }),
)
</script>
<template>
  <q-layout view="hHh lpR fFf">
    <q-header class="studio-header row">
      <div class="row items-center" style="gap: 12px">
        <q-icon name="auto_awesome" class="brand-symbol" />
        <div>
          <div class="brand-name">KJA_BOARD</div>
          <div class="brand-caption">PERSONAL WORKSPACE</div>
        </div>
      </div>
      <div class="appearance-controls row items-center" style="gap: 16px">
        <q-select
          :model-value="settings.app_theme"
          @update:model-value="theme('app_theme', $event)"
          :options="[
            { label: '라이트', value: 'light' },
            { label: '다크', value: 'dark' },
          ]"
          emit-value
          map-options
          label="화면 테마"
          dense
          outlined
        />
        <q-select
          :model-value="settings.mermaid_theme"
          @update:model-value="theme('mermaid_theme', $event)"
          :options="['default', 'dark', 'forest', 'neutral', 'base']"
          label="Mermaid 테마"
          dense
          outlined
        />
      </div>
    </q-header>
    <q-page-container
      ><main class="workspace">
        <q-tabs v-model="tab" class="studio-navigation" align="left"
          ><q-tab
            v-for="item in tabs"
            :key="item[0]"
            :name="item[0]"
            :label="item[1]"
            :icon="item[2]"
        /></q-tabs>
        <q-tab-panels v-if="ready" v-model="tab" class="bg-transparent" keep-alive>
          <q-tab-panel name="documents" class="q-pa-none"
            ><DocumentsPage :theme="settings.mermaid_theme" :dark="settings.app_theme === 'dark'"
          /></q-tab-panel>
          <q-tab-panel name="work" class="q-pa-none"><WorkPage /></q-tab-panel>
          <q-tab-panel name="schedule" class="q-pa-none"><SchedulePage /></q-tab-panel>
          <q-tab-panel name="ui_test" class="q-pa-none"
            ><UiTestPage :dark="settings.app_theme === 'dark'"
          /></q-tab-panel>
          <q-tab-panel name="codegen" class="q-pa-none"><CodeGenPage /></q-tab-panel>
          <q-tab-panel name="mindmap" class="q-pa-none"><MindMapPage /></q-tab-panel>
        </q-tab-panels></main
    ></q-page-container>
  </q-layout>
</template>
