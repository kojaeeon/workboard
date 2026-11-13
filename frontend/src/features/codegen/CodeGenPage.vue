<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, act, error } from '../../shared/api'
import CodeBlock from '../../shared/CodeBlock.vue'
interface Source {
  key: string
  label: string
  location: string
}
interface Inputs {
  table_name: string
  service_name: string
  service_korean_name: string
  package_path: string
  targets: string[]
}
interface GeneratedFile {
  path: string
  content: string
  language: string
}
interface Preview {
  id: string
  archiveName: string
  files: GeneratedFile[]
  status: string
}
const sources = ref<Source[]>([]),
  selectedMd = ref<string | null>(null),
  mdStatus = ref('MD 목록을 읽는 중입니다.'),
  location = ref('저장 문서 및 등록된 로컬 폴더의 하위 MD 파일'),
  yaml = ref(''),
  refreshing = ref(false),
  busy = ref(false),
  files = ref<GeneratedFile[]>([]),
  selectedPath = ref(''),
  generationId = ref(''),
  status = ref('미리보기 실행 전')
const form = reactive<Inputs>({
  table_name: '',
  service_name: '',
  service_korean_name: '',
  package_path: '',
  targets: ['Controller', 'Service', 'VO', 'Mapper'],
})
const selected = computed(() => files.value.find((file) => file.path === selectedPath.value))
let inputVersion = 0,
  mdVersion = 0
function invalidate() {
  inputVersion++
  files.value = []
  selectedPath.value = ''
  generationId.value = ''
  status.value = '입력이 변경되었습니다. 코드 미리보기를 실행하세요.'
}
watch(form, invalidate, { deep: true, flush: 'sync' })
async function refresh() {
  refreshing.value = true
  try {
    await act(async () => {
      const result = await api<{ sources: Source[]; warnings: string[] }>('/codegen/sources')
      sources.value = result.sources
      selectedMd.value = null
      mdStatus.value = result.sources.length
        ? `MD ${result.sources.length}개 · 선택하면 생성 정보가 자동 입력됩니다.`
        : 'MD 보기에서 문서를 저장하거나 로컬 폴더를 등록하세요.'
      result.warnings.forEach((message) => error(new Error(message)))
    })
  } finally {
    refreshing.value = false
  }
}
async function applyMd(key: string | null) {
  selectedMd.value = key
  const version = ++mdVersion
  if (!key) {
    location.value = 'MD 선택 없음 · 현재 입력값을 유지합니다.'
    mdStatus.value = '직접 입력할 수 있습니다.'
    yaml.value = ''
    return
  }
  await act(async () => {
    try {
      const result = await api<{ values: Inputs; yaml: string }>(
        '/codegen/inputs?key=' + encodeURIComponent(key),
      )
      if (version !== mdVersion) return
      const { targets: _targets, ...inputs } = result.values
      Object.assign(form, inputs)
      yaml.value = result.yaml
      location.value = sources.value.find((source) => source.key === key)?.location || ''
      mdStatus.value = '생성 정보 4개를 자동 입력했습니다. 입력값은 직접 수정할 수 있습니다.'
    } catch (e) {
      if (version === mdVersion) {
        mdStatus.value = '자동 입력 실패: ' + (e instanceof Error ? e.message : String(e))
        yaml.value = ''
      }
      throw e
    }
  })
}
async function generate() {
  busy.value = true
  const version = inputVersion
  try {
    await act(async () => {
      try {
        const result = await api<Preview>('/codegen/preview', 'POST', {
          ...form,
          targets: ['Controller', 'Service', 'VO', 'Mapper'].filter((target) =>
            form.targets.includes(target),
          ),
        })
        if (version !== inputVersion) return
        files.value = result.files
        selectedPath.value = result.files[0]?.path || ''
        generationId.value = result.id
        status.value = result.status
      } catch (e) {
        files.value = []
        selectedPath.value = ''
        generationId.value = ''
        status.value = '미리보기를 생성하지 못했습니다.'
        throw e
      }
    })
  } finally {
    busy.value = false
  }
}
onMounted(refresh)
</script>
<template>
  <section class="page">
    <div class="eyebrow">TEMPLATE · CODE GENERATOR</div>
    <div class="page-title">Code Gen</div>
    <p class="muted page-subtitle">테이블과 서비스 정보를 입력하고 생성할 코드를 확인하세요.</p>
    <div class="codegen-layout">
      <div class="column codegen-input column-gap">
        <q-card class="column column-gap full-width"
          ><div class="text-h6">MD에서 자동 입력</div>
          <div class="row items-center full-width" style="gap: 8px">
            <q-select
              :model-value="selectedMd"
              @update:model-value="applyMd"
              :options="sources.map((s) => ({ label: s.label, value: s.key }))"
              label="프론트매터 MD 선택"
              emit-value
              map-options
              clearable
              outlined
              :disable="busy"
              class="grow min-w-0"
            /><q-btn
              flat
              round
              icon="refresh"
              aria-label="MD 목록 새로고침"
              :loading="refreshing"
              :disable="busy"
              @click="refresh"
            />
          </div>
          <div class="text-caption muted" style="overflow-wrap: anywhere">{{ location }}</div>
          <div class="text-body2 muted">{{ mdStatus }}</div>
          <q-expansion-item class="full-width" label="읽어온 프론트매터 보기" icon="data_object"
            ><CodeBlock :content="yaml" language="yaml" /></q-expansion-item
        ></q-card>
        <q-card class="column column-gap full-width"
          ><div class="text-h6">생성 정보</div>
          <q-input
            v-model="form.table_name"
            label="테이블명 *"
            placeholder="KAML.TB_CRA_INSPECTION"
            outlined
            :disable="busy"
            class="full-width"
          /><span class="text-caption muted">스키마.테이블 또는 테이블명</span
          ><q-input
            v-model="form.service_name"
            label="서비스명 *"
            placeholder="CraInspection"
            outlined
            :disable="busy"
            class="full-width"
          /><q-input
            v-model="form.service_korean_name"
            label="서비스 한글명 *"
            placeholder="CRA 정기점검"
            outlined
            :disable="busy"
            class="full-width"
          /><q-input
            v-model="form.package_path"
            label="패키지 경로 *"
            placeholder="com.kaml.cra.inspection"
            outlined
            :disable="busy"
            class="full-width"
          /><span>템플릿 세트 · Spring + MyBatis</span><strong>생성 대상</strong>
          <div class="row" style="gap: 8px">
            <q-checkbox
              v-for="target in ['Controller', 'Service', 'VO', 'Mapper']"
              :key="target"
              v-model="form.targets"
              :val="target"
              :label="target === 'Mapper' ? 'Mapper XML' : target"
              :disable="busy"
            />
          </div>
          <q-btn
            class="full-width"
            color="primary"
            label="코드 미리보기"
            icon="code"
            :loading="busy"
            @click="generate"
          /><span class="text-caption muted">현재는 샘플 템플릿으로 표시합니다.</span></q-card
        >
      </div>
      <div class="column codegen-output column-gap">
        <q-card class="column column-gap full-width"
          ><div class="row items-center justify-between full-width">
            <div class="text-h6">생성 파일</div>
            <div class="row items-center" style="gap: 8px">
              <span class="muted">{{ files.length }}개 파일</span
              ><q-btn
                icon="download"
                flat
                round
                aria-label="전체 다운로드"
                :disable="!generationId || busy"
                :href="generationId ? `/api/codegen/previews/${generationId}/zip` : undefined"
              />
            </div>
          </div>
          <span v-if="!files.length" class="muted">미리보기 후 생성 파일이 표시됩니다.</span
          ><q-btn
            v-for="file in files"
            :key="file.path"
            class="full-width"
            align="left"
            no-caps
            color="primary"
            :flat="file.path !== selectedPath"
            icon="description"
            :label="file.path.split('/').pop()"
            @click="selectedPath = file.path"
        /></q-card>
        <q-card class="column column-gap full-width min-w-0"
          ><template v-if="selected"
            ><div class="text-h6" style="overflow-wrap: anywhere">
              {{ selected.path.split('/').pop() }}
            </div>
            <div class="text-caption muted" style="overflow-wrap: anywhere">
              {{ selected.path }}
            </div>
            <CodeBlock :content="selected.content" :language="selected.language" /></template
          ><template v-else
            ><div class="text-h6">코드 미리보기</div>
            <span class="muted">생성 정보를 입력하고 미리보기를 실행하세요.</span></template
          ><span class="muted">{{ status }}</span></q-card
        >
      </div>
    </div>
  </section>
</template>
<style scoped>
.codegen-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.codegen-input {
  width: 40%;
  min-width: 0;
  gap: 20px;
}
.codegen-output {
  width: 60%;
  min-width: 0;
  gap: 20px;
}
@media (max-width: 1023px) {
  .codegen-layout {
    flex-wrap: wrap;
  }
  .codegen-input,
  .codegen-output {
    width: 100%;
  }
}
</style>
