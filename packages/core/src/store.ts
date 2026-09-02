import type { KeelSeed } from "./types.ts"

export type PageListener = (seed: KeelSeed) => void

let current: KeelSeed | null = null
let processing = 0
const listeners = new Set<PageListener>()
const processingListeners = new Set<(count: number) => void>()

export function getPage<T = unknown>(): KeelSeed<T> {
  if (!current) {
    throw new Error("Keel: no page seed. Bootstrap must call setPage() before mount.")
  }
  return current as KeelSeed<T>
}

export function peekPage<T = unknown>(): KeelSeed<T> | null {
  return current as KeelSeed<T> | null
}

export function setPage(seed: KeelSeed): void {
  current = seed
  for (const listener of listeners) listener(seed)
}

export function subscribe(listener: PageListener): () => void {
  listeners.add(listener)
  if (current) listener(current)
  return () => {
    listeners.delete(listener)
  }
}

export function getProcessing(): number {
  return processing
}

export function beginVisit(): void {
  processing += 1
  for (const listener of processingListeners) listener(processing)
}

export function endVisit(): void {
  processing = Math.max(0, processing - 1)
  for (const listener of processingListeners) listener(processing)
}

export function subscribeProcessing(listener: (count: number) => void): () => void {
  processingListeners.add(listener)
  listener(processing)
  return () => {
    processingListeners.delete(listener)
  }
}
