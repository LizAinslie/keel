import assert from "node:assert/strict"
import { test } from "node:test"
import { cacheSeed, clearPrefetch, prefetchSeed, readSeed } from "./prefetch.ts"
import type { KeelSeed } from "./types.ts"

function seed(page: string): KeelSeed {
  return {
    v: 1,
    page,
    path: `/${page}`,
    params: {},
    data: {},
    errors: {},
    theme: { id: "t", version: "1" },
    entry: "/x.js",
    css: [],
    host: "#__keel_root",
  }
}

test("prefetchSeed dedupes inflight loads", async () => {
  clearPrefetch()
  let calls = 0
  let resolveLoad: (value: KeelSeed) => void = () => undefined
  const pending = new Promise<KeelSeed>((resolve) => {
    resolveLoad = resolve
  })
  const load = () => {
    calls += 1
    return pending
  }
  const a = prefetchSeed("/p", load)
  const b = prefetchSeed("/p", load)
  resolveLoad(seed("home"))
  assert.equal(await a, await b)
  assert.equal(calls, 1)
})

test("seed cache evicts the oldest entry past 32", () => {
  clearPrefetch()
  for (let i = 0; i < 33; i += 1) {
    cacheSeed(`/p/${i}`, seed(`p${i}`))
  }
  assert.equal(readSeed("/p/0"), null)
  assert.ok(readSeed("/p/32"))
})
