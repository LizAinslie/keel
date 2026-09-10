import assert from "node:assert/strict"
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { test } from "node:test"
import { originFromHost, schemaUrl } from "./schema.ts"
import { emitTypescript, scaffoldPack } from "./scaffold.ts"

const schema = {
  format: "keel/1" as const,
  pagesName: "HarborPages",
  pages: {
    "harbor.home": { type: "HomePage", path: "/", methods: ["GET"] },
    "harbor.notFound": { type: "NotFoundPage", path: "/__not-found", methods: ["GET"] },
  },
  actions: {
    "harbor.setName": { in: "SetNameIn", out: "SetNameOut" },
  },
  types: {
    HomePage: { kind: "object", fields: { greeting: "string" } },
    NotFoundPage: { kind: "object", fields: { path: "string" } },
    SetNameIn: { kind: "object", fields: { displayName: "string" } },
    SetNameOut: { kind: "object", fields: { ok: "boolean" } },
  },
}

test("originFromHost defaults localhost to http and domains to https", () => {
  assert.equal(originFromHost("127.0.0.1:8090"), "http://127.0.0.1:8090")
  assert.equal(originFromHost("example.com"), "https://example.com")
  assert.equal(schemaUrl("example.com"), "https://example.com/__keel/schema")
})

test("emitTypescript writes pages and actions", () => {
  const ts = emitTypescript(schema, "HarborPages")
  assert.match(ts, /export interface HomePage/)
  assert.match(ts, /greeting: string/)
  assert.match(ts, /"harbor.home": HomePage/)
  assert.match(ts, /export interface HarborActions/)
  assert.match(ts, /"harbor.setName": \{ in: SetNameIn; out: SetNameOut \}/)
})

test("scaffoldPack writes svelte pages from a schema file", async () => {
  const root = mkdtempSync(join(tmpdir(), "keel-scaffold-"))
  const schemaFile = join(root, "schema.json")
  writeFileSync(schemaFile, JSON.stringify(schema))
  const out = join(root, "pack")
  const written = await scaffoldPack({
    outDir: out,
    schemaPath: schemaFile,
    id: "harbor",
  })
  assert.ok(written.includes("src/pages/harbor/home/+page.svelte"))
  assert.ok(written.includes("src/pages/harbor/notFound/+page.svelte"))
  const page = readFileSync(join(out, "src/pages/harbor/home/+page.svelte"), "utf8")
  assert.match(page, /page<HomePage>/)
  assert.match(page, /from "\.\.\/\.\.\/\.\.\/lib\/page-types"/)
  const vite = readFileSync(join(out, "vite.config.ts"), "utf8")
  assert.match(vite, /notFound: "harbor.notFound"/)
  assert.ok(existsSync(join(out, "src/lib/page-types.json")))
  assert.ok(existsSync(join(out, "src/bootstrap.ts")))
})

test("scaffoldPack refuses to overwrite without --force", async () => {
  const root = mkdtempSync(join(tmpdir(), "keel-scaffold-"))
  await scaffoldPack({ outDir: root, schema, id: "demo" })
  await assert.rejects(
    () => scaffoldPack({ outDir: root, schema, id: "demo" }),
    /exists/,
  )
})

test("react framework is reserved", async () => {
  const root = mkdtempSync(join(tmpdir(), "keel-scaffold-"))
  await assert.rejects(
    () => scaffoldPack({ outDir: root, schema, framework: "react" }),
    /not generated yet/,
  )
})
