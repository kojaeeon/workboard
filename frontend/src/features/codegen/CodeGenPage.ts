import { defineComponent } from 'vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, act, error } from '../../shared/api'
import CodeBlock from '../../shared/CodeBlock.vue'
import type { QueryResult } from '../database/types'
import './CodeGenPage.css'
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
  warnings?: string[]
}

export default defineComponent({
  components: { CodeBlock },
  props: {
    queryId: { type: String, required: false },
  },
  emits: ['clear-query'],
  setup(props, { emit }) {
    const querySource = ref<QueryResult>(),
      queryLoading = ref(false),
      warnings = ref<string[]>([])
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
      warnings.value = []
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
      if (props.queryId && !querySource.value) {
        error(new Error('DB조회에서 결과를 다시 전달하세요.'))
        return
      }
      busy.value = true
      const version = inputVersion
      try {
        await act(async () => {
          try {
            const request = {
              ...form,
              targets: ['Controller', 'Service', 'VO', 'Mapper'].filter((target) =>
                form.targets.includes(target),
              ),
            }
            const result = await api<Preview>(
              props.queryId ? '/codegen/query-preview' : '/codegen/preview',
              'POST',
              props.queryId ? { queryId: props.queryId, request } : request,
            )
            if (version !== inputVersion) return
            files.value = result.files
            selectedPath.value = result.files[0]?.path || ''
            generationId.value = result.id
            status.value = result.status
            warnings.value = result.warnings || []
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
    let queryVersion = 0
    watch(
      () => props.queryId,
      async (id) => {
        const version = ++queryVersion
        invalidate()
        querySource.value = undefined
        queryLoading.value = false
        if (!id) return
        queryLoading.value = true
        try {
          const result = await api<QueryResult>('/database/results/' + encodeURIComponent(id))
          if (version !== queryVersion) return
          querySource.value = result
          Object.assign(form, {
            table_name: result.workItem.table || '',
            service_name: result.workItem.service || '',
            service_korean_name: result.workItem.serviceNm || '',
            package_path: result.workItem.package_path || '',
          })
          status.value =
            'DB 조회 결과를 받았습니다. 생성 정보를 확인하고 코드 미리보기를 실행하세요.'
        } catch (e) {
          if (version === queryVersion) {
            status.value = '조회 결과를 불러오지 못했습니다. DB조회에서 다시 실행하세요.'
            error(e)
          }
        } finally {
          if (version === queryVersion) queryLoading.value = false
        }
      },
      { immediate: true },
    )
    onMounted(refresh)

    return {
      props,
      emit,
      querySource,
      queryLoading,
      warnings,
      sources,
      selectedMd,
      mdStatus,
      location,
      yaml,
      refreshing,
      busy,
      files,
      selectedPath,
      generationId,
      status,
      form,
      selected,
      refresh,
      applyMd,
      generate,
    }
  },
})
