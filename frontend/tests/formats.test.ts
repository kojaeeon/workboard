import { describe, expect, it, vi } from 'vitest'
import fixtures from './fixtures/original-formats.json'
import { exportGraph, importGraph } from '../src/features/mindmap/formats'
import type { Graph } from '../src/features/mindmap/model'

// Conversion unit tests leave server-side graph validation to the Java tests.
vi.mock('../src/shared/api', () => ({
  api: async (_path: string, _method: string, graph: Graph) => graph,
}))
const sorted = (g: Graph) => ({
  ...g,
  nodes: [...g.nodes].sort((a, b) => a.id.localeCompare(b.id)),
  edges: [...g.edges].sort((a, b) => a.id.localeCompare(b.id)),
})
describe('original Python format compatibility (804140b)', () => {
  for (const kind of ['dot', 'flowchart', 'mindmap', 'json'] as const) {
    it(`imports original ${kind} and exports it without loss`, async () => {
      const { graph } = await importGraph(fixtures.formats[kind], kind)
      expect(sorted(graph)).toEqual(sorted(fixtures.graph as Graph))
      const { graph: roundtrip } = await importGraph(await exportGraph(graph, kind), kind)
      expect(sorted(roundtrip)).toEqual(sorted(fixtures.graph as Graph))
    })
  }
  it('keeps cycles and multiple parents as additional relations', async () => {
    const { graph } = await importGraph('digraph {a -> b; b -> c; c -> a; a -> c;}', 'dot')
    expect(graph.edges.filter((e) => !e.tree)).toHaveLength(2)
  })
  it('keeps repeated plain mindmap labels as distinct nodes', async () => {
    const { graph } = await importGraph('mindmap\n  root\n    child\n    child', 'mermaid')
    expect(graph.nodes).toHaveLength(3)
    expect(new Set(graph.nodes.map((n) => n.id)).size).toBe(3)
  })
  it('preserves relation arrows and ignores unsafe directives', async () => {
    const { graph, warnings } = await importGraph(
      'flowchart LR\nA["화면"] -.->|호출| B["API"]\nB <--> A\nclick A "javascript:alert(1)"',
      'mermaid',
    )
    expect(graph.edges[0].dashed).toBe(true)
    expect(graph.edges[1].bidirectional).toBe(true)
    expect(warnings.some((w) => w.includes('click'))).toBe(true)
  })
  it('decodes literal backslashes, quotes, and newlines exactly once', async () => {
    const graph = structuredClone(fixtures.graph) as Graph
    graph.nodes[0].label = 'C:\\new\\folder "이름"\n다음 행'
    const imported = await importGraph(await exportGraph(graph, 'dot'), 'dot')
    expect(imported.graph.nodes[0].label).toBe(graph.nodes[0].label)
  })
})
