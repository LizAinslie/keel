import assert from "node:assert/strict"
import { test } from "node:test"
import { KEEL_HEADERS, KEEL_NAVIGATE_PATH, KEEL_SEED_VERSION } from "./types.ts"

test("protocol constants match the Kotlin core", () => {
  assert.equal(KEEL_SEED_VERSION, 1)
  assert.equal(KEEL_NAVIGATE_PATH, "/__keel/navigate")
  assert.equal(KEEL_HEADERS.visit, "X-Keel-Visit")
  assert.equal(KEEL_HEADERS.only, "X-Keel-Only")
})
