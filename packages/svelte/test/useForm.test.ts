import assert from "node:assert/strict"
import { mkdirSync, readFileSync, writeFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"
import { test } from "node:test"
import { compileModule } from "svelte/compiler"
import { router } from "@kolektiv/keel"

async function loadUseForm() {
  const source = readFileSync(new URL("../dist/useForm.svelte.js", import.meta.url), "utf8")
  const compiled = compileModule(source, { filename: "useForm.svelte.js", generate: "client" })
  const dir = join(dirname(fileURLToPath(import.meta.url)), ".compiled")
  mkdirSync(dir, { recursive: true })
  const out = join(dir, "useForm.js")
  writeFileSync(out, compiled.js.code)
  const mod = (await import(`${pathToFileURL(out).href}?t=${Date.now()}`)) as {
    useForm: (initial: Record<string, unknown>) => {
      data: Record<string, unknown>
      errors: Record<string, string[]>
      processing: boolean
      wasSuccessful: boolean
      isDirty: boolean
      set: (key: string, value: unknown) => void
      reset: () => void
      post: (href: string) => Promise<void>
    }
  }
  return mod.useForm
}

test("useForm tracks dirtiness and reset", async () => {
  const useForm = await loadUseForm()
  const form = useForm({ body: "hi" })
  assert.equal(form.isDirty, false)
  form.set("body", "there")
  assert.equal(form.data.body, "there")
  assert.equal(form.isDirty, true)
  form.reset()
  assert.equal(form.data.body, "hi")
  assert.equal(form.isDirty, false)
})

test("useForm processing errors and success", async () => {
  const useForm = await loadUseForm()
  const form = useForm({ body: "hi" })
  const original = router.visit
  router.visit = async (_href, options) => {
    assert.equal(form.processing, true)
    options?.onError?.({ body: ["required"] })
  }
  try {
    await form.post("/form")
    assert.equal(form.processing, false)
    assert.deepEqual(form.errors.body, ["required"])
    assert.equal(form.wasSuccessful, false)
    router.visit = async (_href, options) => {
      options?.onSuccess?.({} as never)
    }
    await form.post("/form")
    assert.equal(form.wasSuccessful, true)
    assert.deepEqual(form.errors, {})
  } finally {
    router.visit = original
  }
})
