import type { PageHead } from "@kolektiv/keel"

const ATTR = "data-keel-head"

export type HeadInput = {
  title?: string | null
  description?: string | null
  canonical?: string | null
  image?: string | null
  type?: string | null
  html?: string | null
}

export function setTitle(title: string): void {
  if (typeof document === "undefined") return
  document.title = title
}

export function applyHead(head: HeadInput): () => void {
  if (typeof document === "undefined") return () => undefined
  if (head.html) return applyHeadHtml(head.html, head.title)
  clearKeelHead()
  const created: Element[] = []
  if (head.title) {
    document.title = head.title
    const title = document.querySelector("title")
    if (title) {
      title.setAttribute(ATTR, "")
      title.textContent = head.title
    }
    upsert("meta", { property: "og:title", content: head.title }, created)
    upsert("meta", { name: "twitter:title", content: head.title }, created)
  }
  if (head.description) {
    upsert("meta", { name: "description", content: head.description }, created)
    upsert("meta", { property: "og:description", content: head.description }, created)
    upsert("meta", { name: "twitter:description", content: head.description }, created)
  }
  if (head.canonical) {
    upsert("link", { rel: "canonical", href: head.canonical }, created)
    upsert("meta", { property: "og:url", content: head.canonical }, created)
  }
  if (head.image) {
    upsert("meta", { property: "og:image", content: head.image }, created)
    upsert("meta", { name: "twitter:image", content: head.image }, created)
    upsert("meta", { name: "twitter:card", content: "summary_large_image" }, created)
  } else {
    upsert("meta", { name: "twitter:card", content: "summary" }, created)
  }
  upsert("meta", { property: "og:type", content: head.type ?? "website" }, created)
  return () => {
    for (const el of created) el.remove()
  }
}

function applyHeadHtml(html: string, title?: string | null): () => void {
  clearKeelHead()
  const created: Element[] = []
  const template = document.createElement("template")
  template.innerHTML = html
  for (const node of [...template.content.childNodes]) {
    if (!(node instanceof Element)) continue
    node.setAttribute(ATTR, "")
    if (node.tagName === "TITLE") {
      document.title = node.textContent ?? title ?? document.title
      const existing = document.querySelector("title")
      if (existing) {
        existing.setAttribute(ATTR, "")
        existing.textContent = node.textContent
        continue
      }
    }
    document.head.appendChild(node)
    created.push(node)
  }
  if (title) document.title = title
  return () => {
    for (const el of created) el.remove()
  }
}

export function syncHead(nodes: Array<{ tag: string; attrs: Record<string, string> }>): () => void {
  if (typeof document === "undefined") return () => undefined
  const created: Element[] = []
  for (const node of nodes) upsert(node.tag, node.attrs, created)
  return () => {
    for (const el of created) el.remove()
  }
}

function clearKeelHead(): void {
  if (typeof document === "undefined") return
  for (const el of [...document.head.querySelectorAll(`[${ATTR}]`)]) {
    if (el.tagName === "TITLE") continue
    el.remove()
  }
}

function upsert(tag: string, attrs: Record<string, string>, created: Element[]): void {
  const el = document.createElement(tag)
  for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, value)
  el.setAttribute(ATTR, "")
  document.head.appendChild(el)
  created.push(el)
}

export function fromPageHead(head: PageHead | null | undefined, fallback?: HeadInput): HeadInput {
  return {
    title: fallback?.title ?? head?.title,
    description: fallback?.description ?? head?.description,
    canonical: fallback?.canonical ?? head?.canonical,
    image: fallback?.image ?? head?.image,
    type: fallback?.type ?? head?.type,
    html: fallback?.html ?? head?.html,
  }
}
