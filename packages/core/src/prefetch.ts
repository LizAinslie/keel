import type { KeelSeed } from "./types.ts"

interface Entry {
  seed: KeelSeed
  expires: number
}

const MAX_SEEDS = 32
const cache = new Map<string, Entry>()
const inflight = new Map<string, Promise<KeelSeed>>()
const modules = new Map<string, Promise<unknown>>()

export function cacheSeed(url: string, seed: KeelSeed, ttlMs = 30_000): void {
  if (cache.has(url)) cache.delete(url)
  cache.set(url, { seed, expires: Date.now() + ttlMs })
  while (cache.size > MAX_SEEDS) {
    const oldest = cache.keys().next().value
    if (oldest === undefined) break
    cache.delete(oldest)
  }
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

export function prefetchSeed(url: string, load: () => Promise<KeelSeed>): Promise<KeelSeed> {
  const cached = readSeed(url)
  if (cached) return Promise.resolve(cached)
  const pending = inflight.get(url)
  if (pending) return pending
  const request = load().finally(() => {
    inflight.delete(url)
  })
  inflight.set(url, request)
  return request
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
  inflight.clear()
  modules.clear()
}
