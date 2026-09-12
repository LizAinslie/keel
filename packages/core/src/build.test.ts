import assert from "node:assert/strict"
import { test } from "node:test"
import { isBuildMismatch } from "./router.ts"

test("build mismatch fires when the mounted and response builds differ", () => {
  assert.equal(isBuildMismatch("abc123", "abc123"), false)
  assert.equal(isBuildMismatch("abc123", "def456"), true)
})

test("unknown builds on either side never trigger a reload", () => {
  assert.equal(isBuildMismatch(null, "def456"), false)
  assert.equal(isBuildMismatch(undefined, "def456"), false)
  assert.equal(isBuildMismatch("abc123", null), false)
  assert.equal(isBuildMismatch("abc123", undefined), false)
  assert.equal(isBuildMismatch("", ""), false)
})
