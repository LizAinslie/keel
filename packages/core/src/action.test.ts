import assert from "node:assert/strict"
import { test } from "node:test"
import { action, ActionError } from "./action.ts"

test("action posts JSON and returns the data envelope", async () => {
  const original = globalThis.fetch
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    assert.equal(String(input), "/__keel/action/echo")
    assert.equal(init?.method, "POST")
    assert.equal((init?.headers as Record<string, string>)["Content-Type"], "application/json")
    assert.equal(init?.body, JSON.stringify({ message: "ping" }))
    return new Response(JSON.stringify({ data: { message: "ping" } }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    })
  }) as typeof fetch
  try {
    const out = await action<{ message: string }, { message: string }>("echo", { message: "ping" })
    assert.deepEqual(out, { message: "ping" })
  } finally {
    globalThis.fetch = original
  }
})

test("action throws ActionError on 422", async () => {
  const original = globalThis.fetch
  globalThis.fetch = (async () =>
    new Response(JSON.stringify({ errors: { displayName: ["too short"] } }), {
      status: 422,
      headers: { "Content-Type": "application/json" },
    })) as typeof fetch
  try {
    await assert.rejects(
      () => action("harbor.setName", { displayName: "x" }),
      (error: unknown) => {
        assert.ok(error instanceof ActionError)
        assert.deepEqual(error.errors, { displayName: ["too short"] })
        return true
      },
    )
  } finally {
    globalThis.fetch = original
  }
})
