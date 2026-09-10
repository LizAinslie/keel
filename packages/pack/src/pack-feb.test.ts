import assert from "node:assert/strict"
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { test } from "node:test"
import { unzipSync } from "fflate"
import { packFeb } from "./pack-feb.ts"

function writeDist(root: string): string {
  const dist = join(root, "dist")
  mkdirSync(join(dist, "pages"), { recursive: true })
  writeFileSync(
    join(dist, "manifest.json"),
    JSON.stringify({
      format: "keel/1",
      id: "demo",
      version: "0.1.0",
      framework: "svelte",
      pages: {
        "demo.home": { module: "pages/home.js" },
      },
    }),
  )
  writeFileSync(join(dist, "bootstrap.js"), "export {}\n")
  writeFileSync(join(dist, "pages/home.js"), "export async function mount() {}\n")
  mkdirSync(join(dist, ".vite"), { recursive: true })
  writeFileSync(join(dist, ".vite/manifest.json"), "{}")
  return dist
}

test("zip contains manifest.json at the archive root", () => {
  const root = mkdtempSync(join(tmpdir(), "keel-pack-"))
  const dist = writeDist(root)
  const out = join(root, "demo.feb")
  packFeb({ distDir: dist, outFile: out })
  const files = unzipSync(readFileSync(out))
  assert.ok("manifest.json" in files)
  assert.ok("bootstrap.js" in files)
  assert.ok("pages/home.js" in files)
  assert.equal(Object.keys(files).some((name) => name.startsWith(".vite")), false)
})

test("contract rejects unknown page ids", () => {
  const root = mkdtempSync(join(tmpdir(), "keel-pack-"))
  const dist = writeDist(root)
  const contract = join(root, "page-types.ts")
  writeFileSync(
    contract,
    `export interface Pages {\n  "demo.other": Other\n}\n`,
  )
  assert.throws(
    () => packFeb({ distDir: dist, outFile: join(root, "demo.feb"), contract }),
    /unknown page id 'demo.home'/,
  )
})

test("contract from Pages keys allows implemented ids", () => {
  const root = mkdtempSync(join(tmpdir(), "keel-pack-"))
  const dist = writeDist(root)
  const contract = join(root, "page-types.ts")
  writeFileSync(
    contract,
    `export interface DemoPages {\n  "demo.home": HomePage\n  "demo.about": AboutPage\n}\n`,
  )
  const out = join(root, "demo.feb")
  packFeb({ distDir: dist, outFile: out, contract })
  assert.ok("manifest.json" in unzipSync(readFileSync(out)))
})
