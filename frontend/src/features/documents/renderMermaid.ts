import mermaid from 'mermaid'

let queue: Promise<unknown> = Promise.resolve()
let diagramId = 0

export function renderMermaid(source: string, theme: string) {
  const result = queue.then(async () => {
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: theme as any })
    const output = await mermaid.render(`diagram_${++diagramId}`, source)
    return output.svg
  })
  queue = result.catch(() => {})
  return result
}
