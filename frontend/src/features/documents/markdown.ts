export interface Block {
  kind: 'markdown' | 'mermaid' | 'code' | 'frontmatter'
  source: string
  language?: string
}
export function blocks(content: string): Block[] {
  content = content.replace(/^\uFEFF/, '')
  const result: Block[] = []
  const fm = content.match(/^---[ \t]*\r?\n([\s\S]*?)\r?\n(?:---|\.\.\.)[ \t]*(?:\r?\n|$)/)
  if (fm) {
    result.push({ kind: 'frontmatter', source: fm[1], language: 'yaml' })
    content = content.slice(fm[0].length)
  }
  const lines = content.match(/[^\n]*\n|[^\n]+$/g) || []
  let markdown = ''
  for (let i = 0; i < lines.length; i++) {
    const match = lines[i].match(/^ {0,3}(`{3,}|~{3,})(.*?)\r?\n?$/)
    if (!match) {
      markdown += lines[i]
      continue
    }
    const fence = match[1],
      language = match[2].trim()
    const closing = new RegExp(`^ {0,3}${fence[0]}{${fence.length},}\\s*$`)
    let end = i + 1
    while (end < lines.length && !closing.test(lines[end])) end++
    if (end === lines.length) {
      markdown += lines.slice(i).join('')
      break
    }
    if (markdown) {
      result.push({ kind: 'markdown', source: markdown })
      markdown = ''
    }
    result.push({
      kind: language.toLowerCase() === 'mermaid' ? 'mermaid' : 'code',
      language: language.split(/\s/)[0],
      source: lines.slice(i + 1, end).join(''),
    })
    i = end
  }
  if (markdown) result.push({ kind: 'markdown', source: markdown })
  return result
}
export function headings(content: string) {
  const lines = content.replace(/^\uFEFF/, '').split(/\r?\n/),
    result: { level: number; label: string; line: number }[] = []
  let fence = '',
    frontmatterEnd = -1
  if (lines[0]?.trim() === '---')
    frontmatterEnd = lines.findIndex((s, i) => i > 0 && ['---', '...'].includes(s.trim()))
  lines.forEach((line, i) => {
    if (i <= frontmatterEnd) return
    const marker = line.match(/^ {0,3}(`{3,}|~{3,})/)
    if (fence) {
      if (new RegExp(`^ {0,3}${fence[0]}{${fence.length},}\\s*$`).test(line)) fence = ''
      return
    }
    if (marker) {
      fence = marker[1]
      return
    }
    const match = line.match(/^ {0,3}(#{1,6})(?:\s+|$)(.*)$/)
    if (match)
      result.push({
        level: match[1].length,
        label: match[2].replace(/\s+#+\s*$/, '').trim() || '(빈 제목)',
        line: i + 1,
      })
    else if (line.trim() && /^ {0,3}(=+|-+)\s*$/.test(lines[i + 1] || ''))
      result.push({
        level: lines[i + 1].trim().startsWith('=') ? 1 : 2,
        label: line.trim(),
        line: i + 1,
      })
  })
  return result
}
