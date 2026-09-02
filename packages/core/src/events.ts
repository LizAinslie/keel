import type { RouterEvent, RouterListener } from "./types.ts"

const listeners = new Map<RouterEvent, Set<RouterListener>>()

export function on(event: RouterEvent, listener: RouterListener): () => void {
  let set = listeners.get(event)
  if (!set) {
    set = new Set()
    listeners.set(event, set)
  }
  set.add(listener)
  return () => {
    set?.delete(listener)
  }
}

export function emit(event: RouterEvent, detail: Record<string, unknown> = {}): void {
  const set = listeners.get(event)
  if (set) {
    for (const listener of set) listener(detail)
  }
  if (typeof document !== "undefined") {
    document.dispatchEvent(new CustomEvent(`keel:${event}`, { detail }))
  }
}
