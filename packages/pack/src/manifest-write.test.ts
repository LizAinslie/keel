import assert from "node:assert/strict"
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { test } from "node:test"
import { cssFromBundle, writePackManifest } from "./manifest-write.ts"

test("cssFromBundle walks entry and imported chunk viteMetadata.importedCss uniquely", () => {
  const css = cssFromBundle("pages/home.js", {
    "pages/home.js": {
      type: "chunk",
      fileName: "pages/home.js",
      imports: ["chunks/shared.js"],
      viteMetadata: { importedCss: ["assets/a.css", "assets/b.css"] },
    },
    "chunks/shared.js": {
      type: "chunk",
      fileName: "chunks/shared.js",
      imports: [],
      viteMetadata: { importedCss: ["assets/b.css", "assets/c.css"] },
    },
  })
  assert.deepEqual(css, ["assets/a.css", "assets/b.css", "assets/c.css"])
})

test("writePackManifest writes keel/1 JSON", () => {
  const outDir = mkdtempSync(join(tmpdir(), "keel-manifest-"))
  const manifest = writePackManifest({
    outDir,
    id: "harbor",
    version: "0.1.0",
    framework: "svelte",
    host: "#__keel_root",
    pages: {
      "harbor.home": { module: "pages/harbor.home.js", css: ["assets/styles.css"] },
    },
    notFound: "pages/harbor.notFound.js",
  })
  assert.equal(manifest.format, "keel/1")
  const written = JSON.parse(readFileSync(join(outDir, "manifest.json"), "utf8")) as typeof manifest
  assert.deepEqual(written, manifest)
  assert.equal(written.notFound, "pages/harbor.notFound.js")
})

test("writePackManifest fails on unknown contract ids", () => {
  const root = mkdtempSync(join(tmpdir(), "keel-manifest-contract-"))
  const contract = join(root, "page-types.ts")
  writeFileSync(contract, `export interface Pages {\n  "demo.other": Other\n}\n`)
  assert.throws(
    () =>
      writePackManifest({
        outDir: root,
        id: "demo",
        version: "0.1.0",
        framework: "svelte",
        host: "#__keel_root",
        pages: {
          "demo.home": { module: "pages/demo.home.js", css: [] },
        },
        contract,
      }),
    /unknown page id 'demo.home'/,
  )
})
