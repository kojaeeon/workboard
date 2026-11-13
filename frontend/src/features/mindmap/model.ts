export interface Style {
  background?: string | null
  color?: string | null
  border_color?: string | null
  font_size?: number | null
  shape?: string | null
  icon?: string | null
}
export interface NodeType {
  name: string
  style: Style
}
export interface MapNode {
  id: string
  label: string
  type: string
  description: string
  file: string
  overrides: Style
  expanded: boolean
}
export interface Edge {
  id: string
  source: string
  target: string
  label: string
  kind: string
  tree: boolean
  color: string
  dashed: boolean
  bidirectional: boolean
}
export interface Summary {
  id: string
  parent: string
  start: number
  end: number
  label: string
  style: { stroke: string; labelColor: string }
}
export interface View {
  direction: number
  scale: number
  pan_x: number
  pan_y: number
  scroll_left: number
  scroll_top: number
}
export interface Graph {
  version: 1
  root: string
  nodes: MapNode[]
  edges: Edge[]
  summaries: Summary[]
  view: View
}
export const uid = () => 'n_' + crypto.randomUUID().replaceAll('-', '').slice(0, 12)
export const defaultView = (): View => ({
  direction: 1,
  scale: 1,
  pan_x: 0,
  pan_y: 0,
  scroll_left: 0,
  scroll_top: 0,
})
export const node = (n: Partial<MapNode> & { id: string; label: string }): MapNode => ({
  type: '서비스',
  description: '',
  file: '',
  overrides: {},
  expanded: true,
  ...n,
})
export const edge = (e: Partial<Edge> & { id: string; source: string; target: string }): Edge => ({
  label: '',
  kind: '참조',
  tree: false,
  color: '#b89a60',
  dashed: true,
  bidirectional: false,
  ...e,
})
export function newGraph(): Graph {
  const id = uid()
  return {
    version: 1,
    root: id,
    nodes: [node({ id, label: '새 마인드맵' })],
    edges: [],
    summaries: [],
    view: defaultView(),
  }
}
export function fromNetwork(nodes: MapNode[], edges: Edge[], preferred?: string): Graph {
  if (!nodes.length) throw new Error('가져올 노드가 없습니다.')
  const incoming = new Set(edges.map((e) => e.target)),
    candidates = nodes.filter((n) => !incoming.has(n.id)),
    ids = new Set(nodes.map((n) => n.id))
  let root =
    preferred && ids.has(preferred) ? preferred : candidates.length === 1 ? candidates[0].id : ''
  if (!root) {
    root = 'import_' + uid()
    nodes.unshift(node({ id: root, label: '가져온 맵', type: '그룹' }))
  }
  const reached = new Set([root]),
    frontier = [root]
  edges.forEach((e) => (e.tree = false))
  while (reached.size < nodes.length) {
    if (!frontier.length) {
      const seed = nodes.find((n) => !reached.has(n.id))!.id
      edges.push(
        edge({ id: 'join_' + uid(), source: root, target: seed, tree: true, kind: '계층' }),
      )
      reached.add(seed)
      frontier.push(seed)
    }
    const current = frontier.shift()!
    edges.forEach((e) => {
      if (e.source === current && !reached.has(e.target)) {
        e.tree = true
        reached.add(e.target)
        frontier.push(e.target)
      }
    })
  }
  return { version: 1, root, nodes, edges, summaries: [], view: defaultView() }
}
