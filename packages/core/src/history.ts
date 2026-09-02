import type { KeelSeed } from "./types.ts"

const STATE_KEY = "keel"

export function pushSeed(seed: KeelSeed, replace: boolean): void {
  if (typeof history === "undefined") return
  const url = seed.path
  const state = { [STATE_KEY]: seed }
  if (replace) history.replaceState(state, "", url)
  else history.pushState(state, "", url)
}

export function seedFromHistory(event: PopStateEvent): KeelSeed | null {
  const state = event.state as { [STATE_KEY]?: KeelSeed } | null
  return state?.[STATE_KEY] ?? null
}

export function replaceSeed(seed: KeelSeed): void {
  pushSeed(seed, true)
}
