import { Dialog, Notify } from 'quasar'
export async function api<T = void>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch('/api' + path, {
    method,
    headers: body instanceof FormData ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
  })
  if (!response.ok) {
    const result = await response
      .json()
      .catch(() => ({ message: `요청 실패 (${response.status})` }))
    throw new Error(result.message || `요청 실패 (${response.status})`)
  }
  return response.status === 204 || response.headers.get('content-length') === '0'
    ? (undefined as T)
    : await response.text().then((text) => (text ? JSON.parse(text) : undefined))
}
export function error(e: unknown) {
  Notify.create({ type: 'negative', message: e instanceof Error ? e.message : String(e) })
}
export function success(message: string) {
  Notify.create({ type: 'positive', message })
}
export async function act(task: () => Promise<unknown>) {
  try {
    await task()
  } catch (e) {
    error(e)
  }
}
export function confirm(message: string, ok = '계속'): Promise<boolean> {
  return new Promise((resolve) =>
    Dialog.create({
      message,
      cancel: { label: '취소', flat: true },
      ok: { label: ok, color: 'primary' },
    })
      .onOk(() => resolve(true))
      .onCancel(() => resolve(false))
      .onDismiss(() => resolve(false)),
  )
}
export function download(text: string, name: string, type = 'text/plain;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
