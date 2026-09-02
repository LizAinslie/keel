import type { KeelSeed } from "./types.ts"

interface Entry {
  seed: KeelSeed
  expires: number
}

const cache = new Map<string, Entry>()
const modules = new Map<string, Promise<unknown>>()

export function cacheSeed(url: string, seed: KeelSeed, ttlMs = 30_000): void {
  cache.set(url, { seed, expires: Date.now() + ttlMs })
}

export function readSeed(url: string): KeelSeed | null {
  const entry = cache.get(url)
  if (!entry) return null
  if (entry.expires < Date.now()) {
    cache.delete(url)
    return null
  }
  return entry.seed
}

export function warmModule(entry: string): Promise<unknown> {
  let pending = modules.get(entry)
  if (!pending) {
    pending = import(/* @vite-ignore */ entry)
    modules.set(entry, pending)
  }
  return pending
}

export function clearPrefetch(): void {
  cache.clear()
  modules.clear()
}
