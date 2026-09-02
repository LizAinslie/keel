import { router, type Method, type PrefetchMode, type VisitOptions } from "@kolektiv/keel"

export interface KeelActionParams extends VisitOptions {
  href?: string
  prefetch?: PrefetchMode
}

function intercept(event: MouseEvent): boolean {
  if (event.defaultPrevented || event.button !== 0) return false
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return false
  return true
}

export function keel(node: HTMLAnchorElement, params: KeelActionParams = {}) {
  function href(): string {
    return params.href ?? node.getAttribute("href") ?? ""
  }

  function visit(event: MouseEvent) {
    if (!intercept(event)) return
    const method: Method = params.method ?? "get"
    if (method === "get" && node.target === "_blank") return
    event.preventDefault()
    void router.visit(href(), params)
  }

  function prefetch() {
    const mode = params.prefetch
    if (!mode) return
    void router.prefetch(href(), params)
  }

  function onEnter() {
    if (params.prefetch === true || params.prefetch === "hover") prefetch()
  }

  function onDown() {
    if (params.prefetch === "mousedown") prefetch()
  }

  node.addEventListener("click", visit)
  node.addEventListener("pointerenter", onEnter)
  node.addEventListener("pointerdown", onDown)
  if (params.prefetch === "mount") prefetch()

  return {
    update(next: KeelActionParams) {
      params = next
    },
    destroy() {
      node.removeEventListener("click", visit)
      node.removeEventListener("pointerenter", onEnter)
      node.removeEventListener("pointerdown", onDown)
    },
  }
}
