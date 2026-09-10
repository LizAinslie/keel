import assert from "node:assert/strict"
import { test } from "node:test"
import { KEEL_NAVIGATE_PATH } from "./types.ts"
import { visitRequestUrl } from "./router.ts"

test("visit requests the page href, not the navigate proxy", () => {
  assert.equal(visitRequestUrl("/p/first-watch"), "/p/first-watch")
  assert.equal(visitRequestUrl("/posts?q=chain"), "/posts?q=chain")
})

test("navigatePath still builds the compatibility proxy URL", () => {
  assert.equal(
    visitRequestUrl("/p/first-watch", KEEL_NAVIGATE_PATH),
    `${KEEL_NAVIGATE_PATH}?to=${encodeURIComponent("/p/first-watch")}`,
  )
})
