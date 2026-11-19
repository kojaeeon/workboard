import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({ html: true, linkify: true, breaks: false })

export function renderMarkdown(source: string) {
  return DOMPurify.sanitize(markdown.render(source))
}

export function renderCode(source: string, language?: string) {
  return language && hljs.getLanguage(language)
    ? hljs.highlight(source, { language }).value
    : markdown.utils.escapeHtml(source)
}
