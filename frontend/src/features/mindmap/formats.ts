import dot from '@dagrejs/graphlib-dot'
import { api } from '../../shared/api'
import {
  defaultView,
  edge,
  fromNetwork,
  node,
  type Graph,
  type MapNode,
  type Edge,
  type View,
  type Summary,
} from './model'
const q = JSON.stringify
function encode(value: unknown): string {
  return btoa(
    Array.from(new TextEncoder().encode(JSON.stringify(value)), (b) => String.fromCharCode(b)).join(
      '',
    ),
  )
    .replaceAll('+', '-')
    .replaceAll('/', '_')
}
function decode<T = any>(value: string): T {
  return JSON.parse(
    new TextDecoder().decode(
      Uint8Array.from(atob(value.replaceAll('-', '+').replaceAll('_', '/')), (c) =>
        c.charCodeAt(0),
      ),
    ),
  )
}
function without<T extends object>(object: T, ...keys: string[]) {
  return Object.fromEntries(Object.entries(object).filter(([key]) => !keys.includes(key)))
}
export async function exportGraph(value: Graph, format: string): Promise<string> {
  const graph = await api<Graph>('/mindmaps/validate', 'POST', value)
  if (format === 'json') return JSON.stringify(graph, null, 2)
  const { nodes, edges } = graph,
    aliases = Object.fromEntries(nodes.map((n, i) => [n.id, `n${i}`]))
  if (format === 'dot')
    return [
      'digraph ServiceMap {',
      '  rankdir=LR;',
      '  node [shape=box, style=rounded];',
      `  kja_root=${q(aliases[graph.root])};`,
      `  kja_view=${q(encode(graph.view))};`,
      `  kja_summaries=${q(encode(graph.summaries))};`,
      ...nodes.map(
        (n) =>
          `  ${aliases[n.id]} [label=${q(n.label)}, kja_meta=${q(encode(without(n, 'label')))}];`,
      ),
      ...edges.map(
        (e) =>
          `  ${aliases[e.source]} -> ${aliases[e.target]} [label=${q(e.label)}, kja_meta=${q(encode(without(e, 'source', 'target', 'label')))}];`,
      ),
      '}',
    ].join('\n')
  if (!['flowchart', 'mindmap'].includes(format)) throw new Error('지원하지 않는 출력 형식입니다.')
  const lines = [
    `%% kja-root ${aliases[graph.root]}`,
    `%% kja-view ${encode(graph.view)}`,
    `%% kja-summaries ${encode(graph.summaries)}`,
    ...nodes.map((n) => `%% kja-node ${aliases[n.id]} ${encode(without(n, 'label'))}`),
    ...edges.map((e, i) => `%% kja-edge e${i} ${encode(without(e, 'source', 'target', 'label'))}`),
  ]
  const label = (text: string) =>
    Array.from(text, (c) => ('"[](){}<>|#%\\\n\r'.includes(c) ? `#${c.codePointAt(0)};` : c)).join(
      '',
    )
  if (format === 'flowchart') {
    lines.push(
      'flowchart LR',
      ...nodes.map((n) => `  ${aliases[n.id]}["${label(n.label)}"]`),
      ...edges.map(
        (e) =>
          `  ${aliases[e.source]} ${e.bidirectional ? '<-->' : e.dashed && !e.tree ? '-.->' : '-->'}${e.label ? '|' + label(e.label) + '|' : ''} ${aliases[e.target]}`,
      ),
    )
  } else {
    lines.push('mindmap')
    const visit = (id: string, depth: number) => {
      lines.push(
        '  '.repeat(depth) + `${aliases[id]}["${label(nodes.find((n) => n.id === id)!.label)}"]`,
      )
      edges.filter((e) => e.tree && e.source === id).forEach((e) => visit(e.target, depth + 1))
    }
    visit(graph.root, 1)
    edges.forEach((e) => lines.push('%% kja-relation ' + encode(e)))
  }
  return lines.join('\n')
}
async function restore(nodes: MapNode[], edges: Edge[], root?: string): Promise<Graph> {
  if (root) {
    try {
      return await api<Graph>('/mindmaps/validate', 'POST', {
        version: 1,
        root,
        nodes,
        edges,
        summaries: [],
        view: defaultView(),
      })
    } catch {
      /* Non-tree imports need a spanning hierarchy. */
    }
  }
  return fromNetwork(nodes, edges, root)
}
function unquote(value: unknown): string {
  const text = String(value ?? '')
  if (text.startsWith('"') && text.endsWith('"')) {
    try {
      return JSON.parse(text)
    } catch {
      return text.slice(1, -1).replaceAll('\\"', '"').replaceAll('\\n', '\n')
    }
  }
  return text
}
// graphlib-dot removes surrounding quotes and escaped quotes, but retains JSON
// escapes such as \n and \\. Decode those once, as the Python importer does.
function dotText(value: unknown): string {
  const text = String(value ?? '')
  try {
    return JSON.parse('"' + text.replaceAll('"', '\\"') + '"')
  } catch {
    return text.replaceAll('\\n', '\n')
  }
}
function mermaidText(value: string): string {
  const names: Record<string, string> = { quot: '"', lt: '<', gt: '>', amp: '&' }
  return unquote(value.trim()).replace(/#(\d+|quot|lt|gt|amp);/g, (_, c: string) =>
    /^\d+$/.test(c) ? String.fromCodePoint(Number(c)) : names[c],
  )
}
function readNode(text: string, start = 0): [string, string | null, number] {
  const match = text.slice(start).match(/^\s*([A-Za-z_][A-Za-z0-9_-]*)/)
  if (!match) throw new Error('노드 ID를 확인하세요: ' + text.slice(start))
  const key = match[1]
  let pos = start + match[0].length,
    label: string | null = null
  while (pos < text.length && /\s/.test(text[pos])) pos++
  if (pos < text.length && '[({'.includes(text[pos])) {
    const begin = pos,
      stack: string[] = [],
      pairs: Record<string, string> = { ']': '[', ')': '(', '}': '{' }
    let quote = false,
      escaped = false
    while (pos < text.length) {
      const char = text[pos]
      if (quote) {
        if (escaped) escaped = false
        else if (char === '\\') escaped = true
        else if (char === '"') quote = false
      } else if (char === '"') quote = true
      else if ('[({'.includes(char)) stack.push(char)
      else if ('])}'.includes(char)) {
        if (stack.pop() !== pairs[char]) throw new Error('노드 괄호가 맞지 않습니다.')
        if (!stack.length) {
          pos++
          break
        }
      }
      pos++
    }
    if (stack.length || quote) throw new Error('닫히지 않은 노드 괄호입니다.')
    let raw = text.slice(begin, pos)
    while (raw.length >= 2 && '[({'.includes(raw[0]) && '])}'.includes(raw.at(-1)!))
      raw = raw.slice(1, -1)
    label = mermaidText(raw)
  }
  return [key, label, pos]
}
async function importDot(source: string, warnings: string[]): Promise<Graph> {
  const parsed = dot.read(source),
    names: string[] = parsed.nodes(),
    metadata = parsed.graph() || {},
    ids: Record<string, string> = {}
  const nodes: MapNode[] = names.map((name, i) => {
    const attrs = parsed.node(name) || {},
      meta = attrs.kja_meta ? decode(attrs.kja_meta) : {}
    ids[name] = meta.id || `dot_${i}`
    const overrides: Record<string, string> = {}
    if (!attrs.kja_meta)
      for (const [key, attr] of [
        ['color', 'fontcolor'],
        ['background', 'fillcolor'],
        ['border_color', 'color'],
      ]) {
        if (/^#[\da-f]{6}$/i.test(attrs[attr] || '')) overrides[key] = attrs[attr]
      }
    return node({
      ...meta,
      id: ids[name],
      label: dotText(attrs.label ?? name),
      type: meta.type || attrs.kind || (parsed.children(name)?.length ? '그룹' : '서비스'),
      overrides: meta.overrides || overrides,
    })
  })
  const edges: Edge[] = parsed.edges().map((e: any, i: number) => {
    const attrs = parsed.edge(e) || {},
      meta = attrs.kja_meta ? decode(attrs.kja_meta) : {}
    return edge({
      ...meta,
      id: meta.id || `dot_e${i}`,
      source: ids[e.v],
      target: ids[e.w],
      label: dotText(attrs.label || ''),
    })
  })
  names.forEach((name, i) => {
    const parent = parsed.parent(name)
    if (parent) edges.push(edge({ id: `dot_group_${i}`, source: ids[parent], target: ids[name] }))
  })
  const result = await restore(nodes, edges, ids[metadata.kja_root])
  if (metadata.kja_view) result.view = decode(metadata.kja_view)
  if (metadata.kja_summaries) result.summaries = decode(metadata.kja_summaries)
  warnings.push(
    'DOT의 배치 명령과 복잡한 스타일은 Mind Elixir에서 다시 배치합니다. 그룹은 그룹 노드로 변환합니다.',
  )
  return result
}
async function importMermaid(source: string, warnings: string[]): Promise<Graph> {
  const fence = source.match(/```mermaid\s*\n([\s\S]*?)\n```/)
  if (fence) source = fence[1]
  const nodeMeta: Record<string, Partial<MapNode>> = {},
    edgeMeta: Record<string, Partial<Edge>> = {},
    fullRelations: Edge[] = [],
    lines: string[] = []
  let rootAlias = '',
    view: View | undefined,
    summaries: Summary[] = []
  source.split(/\r?\n/).forEach((line) => {
    const text = line.trim(),
      parts = text.split(' ')
    if (text.startsWith('%% kja-node ')) nodeMeta[parts[2]] = decode(parts[3])
    else if (text.startsWith('%% kja-edge ')) edgeMeta[parts[2]] = decode(parts[3])
    else if (text.startsWith('%% kja-root ')) rootAlias = parts.at(-1)!
    else if (text.startsWith('%% kja-summaries ')) summaries = decode(parts.at(-1)!)
    else if (text.startsWith('%% kja-view ')) view = decode(parts.at(-1)!)
    else if (text.startsWith('%% kja-relation ')) fullRelations.push(decode(parts.at(-1)!))
    else if (text && !text.startsWith('%%')) lines.push(line)
  })
  if (!lines.length) throw new Error('Mermaid 코드가 없습니다.')
  const header = lines.shift()!.trim(),
    mindmap = header === 'mindmap'
  if (!mindmap && !/^(?:flowchart|graph)\s+(?:LR|RL|TB|TD|BT)\s*;?$/.test(header))
    throw new Error('mindmap 또는 flowchart/graph LR·TB 형식만 지원합니다.')
  const records = new Map<string, string>(),
    rawEdges: { a: string; b: string; label: string; meta?: Partial<Edge> }[] = []
  const add = (key: string, label: string | null) => {
    if (!records.has(key) || label !== null) records.set(key, label || key)
  }
  if (mindmap) {
    const parents: { depth: number; key: string }[] = []
    lines.forEach((line, i) => {
      if (line.includes('\t')) throw new Error('mindmap 들여쓰기는 탭 대신 공백을 사용하세요.')
      const depth = line.length - line.trimStart().length,
        text = line.trim()
      let key: string, label: string | null, end: number
      try {
        ;[key, label, end] = readNode(text)
        if (end < text.length || label === null) {
          key = `mm_${i}`
          label = mermaidText(text)
        }
      } catch {
        key = `mm_${i}`
        label = mermaidText(text)
      }
      add(key, label)
      while (parents.length && parents.at(-1)!.depth >= depth) parents.pop()
      if (parents.length) rawEdges.push({ a: parents.at(-1)!.key, b: key, label: '' })
      parents.push({ depth, key })
    })
  } else {
    const groups: string[] = []
    for (const line of lines) {
      const text = line.trim().replace(/;$/, '')
      if (text.startsWith('subgraph ')) {
        const key = `group_${records.size}`
        add(key, text.slice(9).replace(/^[ \[\]"]+|[ \[\]"]+$/g, ''))
        if (groups.length) rawEdges.push({ a: groups.at(-1)!, b: key, label: '' })
        groups.push(key)
        continue
      }
      if (text === 'end') {
        if (!groups.length) throw new Error('subgraph 없는 end입니다.')
        groups.pop()
        continue
      }
      if (/^(?:classDef|class|style|click|linkStyle|direction)\b/.test(text)) {
        warnings.push('스타일·클릭 동작·방향 선언은 가져오지 않습니다: ' + text.slice(0, 80))
        continue
      }
      let [a, label, pos] = readNode(text)
      add(a, label)
      if (groups.length && a !== groups.at(-1))
        rawEdges.push({ a: groups.at(-1)!, b: a, label: '' })
      while (pos < text.length) {
        const match = text.slice(pos).match(/^\s*(<-->|-->|---|-\.->|==>)(?:\s*\|([^|]*)\|)?\s*/)
        if (!match)
          throw new Error(
            '지원하지 않는 연결 표현입니다. -->|이름| 형태를 사용하세요: ' + text.slice(pos),
          )
        const [b, label, end] = readNode(text, pos + match[0].length)
        pos = end
        add(b, label)
        rawEdges.push({
          a,
          b,
          label: mermaidText(match[2] || ''),
          meta: { dashed: match[1] === '-.->', bidirectional: match[1] === '<-->' },
        })
        a = b
      }
    }
    if (groups.length) throw new Error('닫히지 않은 subgraph가 있습니다.')
  }
  const ids = Object.fromEntries(
    [...records.keys()].map((key, i) => [key, nodeMeta[key]?.id || `m_${i}`]),
  )
  const nodes = [...records].map(([key, label]) => node({ ...nodeMeta[key], id: ids[key], label }))
  let edges = rawEdges.map((r, i) => {
    const meta = edgeMeta[`e${i}`] || r.meta || {}
    return edge({
      ...meta,
      id: meta.id || `me_${i}`,
      source: ids[r.a],
      target: ids[r.b],
      label: r.label,
    })
  })
  if (mindmap && fullRelations.length) edges = fullRelations
  const result = await restore(nodes, edges, ids[rootAlias])
  result.summaries = summaries
  if (view) result.view = view
  warnings.push(
    'Mermaid의 표현 가능한 노드·계층·관계를 가져왔습니다. 배치와 지원하지 않는 스타일은 다시 지정하세요.',
  )
  return result
}
export async function importGraph(
  source: string,
  format: string,
): Promise<{ graph: Graph; warnings: string[] }> {
  if (new TextEncoder().encode(source).length > 2 * 1024 * 1024)
    throw new Error('가져오기 파일은 2MB 이하로 준비하세요.')
  const warnings: string[] = []
  const value =
    format === 'json'
      ? JSON.parse(source)
      : format === 'dot'
        ? await importDot(source, warnings)
        : await importMermaid(source, warnings)
  return { graph: await api<Graph>('/mindmaps/validate', 'POST', value), warnings }
}
