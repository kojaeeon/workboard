<script setup lang="ts">
import { computed, ref } from 'vue'
import { AgGridVue } from 'ag-grid-vue3'
import {
  AllCommunityModule,
  ModuleRegistry,
  themeQuartz,
  colorSchemeDark,
  colorSchemeLight,
  type GridApi,
} from 'ag-grid-community'
import { useQuasar } from 'quasar'
ModuleRegistry.registerModules([AllCommunityModule])
export interface GridColumn {
  key: string
  label: string
  width: number
  type: string
}
const props = defineProps<{ columns: GridColumn[]; rows: Record<string, unknown>[] }>()
const emit = defineEmits<{ detail: [row: Record<string, unknown>] }>()
const q = useQuasar(),
  search = ref(''),
  selected = ref(0),
  grid = ref<GridApi>()
const columns = computed(() =>
  props.columns.map((c) => ({
    field: c.key,
    headerName: c.label,
    width: c.width,
    filter: c.type === 'number' ? 'agNumberColumnFilter' : 'agTextColumnFilter',
    type: c.type === 'number' ? 'numericColumn' : undefined,
  })),
)
const theme = computed(() =>
  themeQuartz.withPart(q.dark.isActive ? colorSchemeDark : colorSchemeLight),
)
function reset() {
  search.value = ''
  grid.value?.setFilterModel(null)
}
function csv() {
  grid.value?.exportDataAsCsv({
    fileName: 'wiql_result.csv',
    processCellCallback: (p) => {
      const text = p.value == null ? '' : String(p.value)
      return /^\s*[=+@-]/.test(text) ? "'" + text : text
    },
  })
}
defineExpose({ reset })
</script>
<template>
  <div class="column" style="gap: 8px">
    <div class="row items-center" style="gap: 8px">
      <span>{{ rows.length }}건</span><span>선택 {{ selected }}건</span
      ><q-input v-model="search" label="결과 내 검색" clearable dense /><q-btn
        flat
        dense
        label="필터 초기화"
        @click="reset"
      /><q-btn flat dense label="선택 해제" @click="grid?.deselectAll()" /><q-btn
        flat
        dense
        label="CSV 다운로드"
        icon="download"
        @click="csv"
      />
    </div>
    <AgGridVue
      style="height: 384px; width: 100%"
      :theme="theme"
      :column-defs="columns"
      :row-data="rows"
      :suppress-field-dot-notation="true"
      :default-col-def="{
        sortable: true,
        resizable: true,
        filter: true,
        minWidth: 90,
        editable: false,
      }"
      :row-selection="{ mode: 'multiRow', enableClickSelection: true }"
      :pagination="true"
      :pagination-page-size="20"
      :pagination-page-size-selector="[10, 20, 50, 100]"
      :enable-cell-text-selection="true"
      overlay-no-rows-template="조회 결과가 없습니다."
      :quick-filter-text="search || ''"
      @grid-ready="grid = $event.api"
      @selection-changed="selected = $event.api.getSelectedRows().length"
      @row-double-clicked="emit('detail', $event.data)"
    />
  </div>
</template>
