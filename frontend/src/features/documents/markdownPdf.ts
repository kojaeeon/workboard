import { download } from '../../shared/api'
import { blocks } from './markdown'
import { renderCode, renderMarkdown } from './markdownRender'
import { renderMermaid } from './renderMermaid'

export async function exportMarkdownPdf(title: string, markdown: string) {
  const segments = blocks(markdown).filter((block) => block.kind !== 'frontmatter')
  if (!segments.some((block) => block.source.trim())) throw new Error('PDF로 출력할 본문이 없습니다.')

  const html = await Promise.all(
    segments.map(async ({ kind, source, language }) => {
      if (kind === 'markdown') return renderMarkdown(source)
      if (kind === 'mermaid') {
        try {
          return `<figure>${await renderMermaid(source, 'default')}</figure>`
        } catch {
          return `<pre><code>${renderCode(source)}</code></pre>`
        }
      }
      return `<pre><code>${renderCode(source, language)}</code></pre>`
    }),
  )
  const template = document.createElement('template')
  template.innerHTML = html.join('\n')
  await Promise.all([...template.content.querySelectorAll('img')].map(embedImage))

  const response = await fetch('/api/documents/pdf', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, html: template.innerHTML }),
  })
  if (!response.ok) {
    const failure = await response.json().catch(() => ({}))
    throw new Error(failure.message || `PDF 출력 실패 (${response.status})`)
  }

  const filename = response.headers
    .get('Content-Disposition')
    ?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  download(await response.blob(), filename ? decodeURIComponent(filename) : 'document.pdf')
}

async function embedImage(image: HTMLImageElement) {
  const source = image.getAttribute('src') || ''
  image.removeAttribute('srcset')
  if (/^data:image\//i.test(source)) return
  try {
    const url = new URL(source, location.href)
    if (url.origin === location.origin && url.pathname.startsWith('/images/')) {
      const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(5000) })
      const mime = response.headers.get('Content-Type') || ''
      if (response.ok && mime.startsWith('image/')) {
        const file = await response.blob()
        if (file.size <= 2 * 1024 * 1024) {
          image.src = await new Promise<string>((resolve, reject) => {
            const reader = new FileReader()
            reader.onload = () => resolve(String(reader.result))
            reader.onerror = () => reject(reader.error)
            reader.readAsDataURL(file)
          })
          return
        }
      }
    }
  } catch {
    // Keep the alternative text when a local image cannot be embedded.
  }
  image.replaceWith(document.createTextNode(image.alt))
}
