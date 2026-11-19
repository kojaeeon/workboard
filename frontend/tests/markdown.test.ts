import { expect, it } from 'vitest'
import { blocks, headings } from '../src/features/documents/markdown'
it('keeps frontmatter and ordinary code separate from Mermaid', () => {
  const result = blocks(
    '---\ntitle: test\n---\n# 제목\n\n```java\nclass A {}\n```\n~~~mermaid\nflowchart LR\n A --> B\n~~~\n',
  )
  expect(result.map((b) => b.kind)).toEqual(['frontmatter', 'markdown', 'code', 'mermaid'])
  expect(result[2].source).toBe('class A {}\n')
})
it('indexes source lines while excluding YAML and code fences', () => {
  expect(
    headings('---\n# yaml\n---\n# 실제 제목\n```md\n# 코드\n```\n두 번째 제목\n---\n'),
  ).toEqual([
    { level: 1, label: '실제 제목', line: 4 },
    { level: 2, label: '두 번째 제목', line: 8 },
  ])
})

it('separates empty and CRLF frontmatter without dropping the body', () => {
  expect(blocks('---\n---\n# Body')).toEqual([
    { kind: 'frontmatter', source: '', language: 'yaml' },
    { kind: 'markdown', source: '# Body' },
  ])
  expect(blocks('\uFEFF---\r\nsecret: hidden\r\n...\r\n# 본문')).toEqual([
    { kind: 'frontmatter', source: 'secret: hidden', language: 'yaml' },
    { kind: 'markdown', source: '# 본문' },
  ])
})
