import assert from "node:assert/strict"
import { test } from "node:test"
import { KEEL_ACTION_PATH, KEEL_HEADERS, KEEL_NAVIGATE_PATH, KEEL_SEED_VERSION } from "./types.ts"
import { ActionError } from "./action.ts"

test("protocol constants match the Kotlin core", () => {
  assert.equal(KEEL_SEED_VERSION, 1)
  assert.equal(KEEL_NAVIGATE_PATH, "/__keel/navigate")
  assert.equal(KEEL_ACTION_PATH, "/__keel/action")
  assert.equal(KEEL_HEADERS.visit, "X-Keel-Visit")
  assert.equal(KEEL_HEADERS.only, "X-Keel-Only")
})

test("ActionError carries field errors", () => {
  const err = new ActionError({ body: ["required"] })
  assert.equal(err.name, "ActionError")
  assert.deepEqual(err.errors, { body: ["required"] })
})
