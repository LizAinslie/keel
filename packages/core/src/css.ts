const ATTR = "data-keel-css"

export function syncCss(hrefs: string[]): void {
  if (typeof document === "undefined") return
  const next = new Set(hrefs)
  for (const node of document.head.querySelectorAll(`link[${ATTR}]`)) {
    const link = node as HTMLLinkElement
    if (!next.has(link.href) && !next.has(link.getAttribute("href") ?? "")) {
      link.remove()
    }
  }
  const existing = new Set(
    [...document.head.querySelectorAll(`link[${ATTR}]`)].map(
      (n) => (n as HTMLLinkElement).getAttribute("href") ?? "",
    ),
  )
  for (const href of hrefs) {
    if (existing.has(href)) continue
    const link = document.createElement("link")
    link.rel = "stylesheet"
    link.href = href
    link.setAttribute(ATTR, "")
    document.head.appendChild(link)
  }
}
