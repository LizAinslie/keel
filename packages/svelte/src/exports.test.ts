import assert from "node:assert/strict"
import { test } from "node:test"
import { useForm } from "./useForm.ts"

test("useForm tracks dirtiness and reset", () => {
  const form = useForm({ body: "hi" })
  assert.equal(form.isDirty, false)
  form.set("body", "there")
  assert.equal(form.data.body, "there")
  assert.equal(form.isDirty, true)
  form.reset()
  assert.equal(form.data.body, "hi")
  assert.equal(form.isDirty, false)
})
