const ATTR = "data-keel-head"

export function setTitle(title: string): void {
  if (typeof document === "undefined") return
  document.title = title
}

export function syncHead(nodes: Array<{ tag: string; attrs: Record<string, string> }>): () => void {
  if (typeof document === "undefined") return () => undefined
  const created: Element[] = []
  for (const node of nodes) {
    const el = document.createElement(node.tag)
    for (const [key, value] of Object.entries(node.attrs)) el.setAttribute(key, value)
    el.setAttribute(ATTR, "")
    document.head.appendChild(el)
    created.push(el)
  }
  return () => {
    for (const el of created) el.remove()
  }
}
