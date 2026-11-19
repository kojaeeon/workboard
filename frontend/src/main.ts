import { createApp, h, reactive, type Component } from 'vue'
import { Quasar, Dialog, Notify } from 'quasar'
import * as QuasarComponents from 'quasar'
import ko from 'quasar/lang/ko-KR'
import { api, error } from './shared/api'
import 'quasar/dist/quasar.css'
import '@quasar/extras/material-icons/material-icons.css'
import './theme.css'

const pages = {
  documents: () => import('./features/documents/DocumentsPage'),
  work: () => import('./features/work/WorkPage'),
  schedule: () => import('./features/schedule/SchedulePage'),
  'ui-test': () => import('./features/automation/UiTestPage'),
  codegen: () => import('./features/codegen/CodeGenPage'),
  database: () => import('./features/database/DatabaseQueryPage'),
  mindmap: () => import('./features/mindmap/MindMapPage'),
}

type Page = keyof typeof pages
type Settings = { app_theme: string; mermaid_theme: string }

const pageHost = document.querySelector<HTMLElement>('#workspace-pages')!
const message = document.querySelector<HTMLElement>('#workspace-message')!
const links = [...document.querySelectorAll<HTMLAnchorElement>('.workspace-nav a[data-page]')]
const state = reactive({
  settings: { app_theme: 'light', mermaid_theme: 'default' },
  queryId: new URL(location.href).searchParams.get('queryId'),
})
const controls = [
  document.querySelector<HTMLSelectElement>('select[name=app_theme]')!,
  document.querySelector<HTMLSelectElement>('select[name=mermaid_theme]')!,
]
const quasarOptions = {
  components: Object.fromEntries(
    Object.entries(QuasarComponents).filter(([name]) => /^Q[A-Z]/.test(name)),
  ),
  plugins: { Dialog, Notify },
  lang: ko,
  config: { brand: { primary: '#b89a60', secondary: '#7d897b', accent: '#cbb788' } },
}
const mounted = new Set<Page>()
let navigation = 0

function showMessage(text: string) {
  message.textContent = text
  message.hidden = !text
}

function applyTheme() {
  QuasarComponents.Dark.set(state.settings.app_theme === 'dark')
}

function pageLink(page: Page) {
  return links.find((link) => link.dataset.page === page)!
}

function codegen(id: string | null) {
  const url = new URL(pageLink('codegen').href)
  if (id) url.searchParams.set('queryId', id)
  return url
}

function clearQuery() {
  state.queryId = null
  if (location.pathname === codegen(null).pathname) {
    history.replaceState(null, '', codegen(null))
  }
}

function mount(page: Page, host: HTMLElement, widget: Component) {
  const panel = { ...widget, template: host.innerHTML }
  const app = createApp({
    render: () => {
      switch (page) {
        case 'documents':
          return h(panel, {
            dark: state.settings.app_theme === 'dark',
            theme: state.settings.mermaid_theme,
          })
        case 'ui-test':
          return h(panel, { dark: state.settings.app_theme === 'dark' })
        case 'database':
          return h(panel, {
            dark: state.settings.app_theme === 'dark',
            onGenerate: (id: string) => void showPage('codegen', codegen(id), true),
            onInvalidate: (id: string) => {
              if (id === state.queryId) clearQuery()
            },
          })
        case 'codegen':
          return h(panel, { queryId: state.queryId, onClearQuery: clearQuery })
        default:
          return h(panel)
      }
    },
  })
  app.use(Quasar, quasarOptions).mount(host)
  mounted.add(page)
}

async function showPage(page: Page, url: URL, push = false) {
  const request = ++navigation
  showMessage('')
  try {
    let host = pageHost.querySelector<HTMLElement>(`[data-workboard-page="${page}"]`)
    if (!host) {
      const response = await fetch(url)
      if (!response.ok) throw new Error(`화면 요청 실패 (${response.status})`)
      const view = new DOMParser().parseFromString(await response.text(), 'text/html')
      host = view.querySelector<HTMLElement>(`#workspace-pages > [data-workboard-page="${page}"]`)
      if (!host) throw new Error('화면을 찾을 수 없습니다.')
    }
    const widget = mounted.has(page) ? undefined : (await pages[page]()).default
    if (request !== navigation) return

    if (page === 'codegen') state.queryId = url.searchParams.get('queryId')
    if (host.parentElement !== pageHost) pageHost.append(host)
    for (const panel of pageHost.children) (panel as HTMLElement).hidden = panel !== host
    if (widget) mount(page, host, widget)
    links.forEach((link) => {
      if (link.dataset.page === page) link.setAttribute('aria-current', 'page')
      else link.removeAttribute('aria-current')
    })
    const label = pageLink(page).querySelector('[data-page-label]')?.textContent?.trim()
    document.title = label ? `${label} · KJA_BOARD` : 'KJA_BOARD'
    if (push && url.href !== location.href) history.pushState(null, '', url)
  } catch (failure) {
    if (request === navigation) {
      showMessage(failure instanceof Error ? failure.message : String(failure))
      if (!mounted.has(page)) {
        const host = pageHost.querySelector<HTMLElement>(`[data-workboard-page="${page}"]`)
        if (host) host.hidden = true
      }
    }
  }
}

function currentPage(): Page {
  return (links.find((link) => link.pathname === location.pathname)?.dataset.page ??
    'documents') as Page
}

async function start() {
  let settingsError = ''
  try {
    Object.assign(state.settings, await api<Settings>('/settings'))
  } catch (failure) {
    settingsError = failure instanceof Error ? failure.message : String(failure)
  }

  await showPage(currentPage(), new URL(location.href))
  if (settingsError && message.hidden) showMessage(settingsError)
  applyTheme()
  for (const control of controls) {
    const key = control.name as keyof Settings
    control.value = state.settings[key]
    control.disabled = false
    control.addEventListener('change', async () => {
      const value = control.value
      control.disabled = true
      try {
        await api(`/settings/${key}`, 'PUT', { value })
        state.settings[key] = value
        applyTheme()
      } catch (failure) {
        error(failure)
      } finally {
        control.value = state.settings[key]
        control.disabled = false
      }
    })
  }

  for (const link of links) {
    link.addEventListener('click', (event) => {
      if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)
        return
      event.preventDefault()
      const page = link.dataset.page as Page
      const url = page === 'codegen' ? codegen(state.queryId) : new URL(link.href)
      void showPage(page, url, true)
    })
  }
  addEventListener('popstate', () => void showPage(currentPage(), new URL(location.href)))
}

void start()
