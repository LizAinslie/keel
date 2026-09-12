import assert from "node:assert/strict"
import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { svelteFiles } from "./svelte.ts"

function writeTree(files: Record<string, string>): string {
  const root = mkdtempSync(join(tmpdir(), "keel-svelte-files-"))
  for (const [rel, content] of Object.entries(files)) {
    const full = join(root, rel)
    mkdirSync(dirname(full), { recursive: true })
    writeFileSync(full, content)
  }
  return root
}

function relLayouts(pagesDir: string, layouts: string[]): string[] {
  return layouts.map((file) => relative(pagesDir, file).replaceAll("\\", "/"))
}

test("nested directories become dotted page ids", () => {
  const pagesDir = writeTree({
    "harbor/home/+page.svelte": "<h1>home</h1>\n",
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.id, "harbor.home")
  assert.equal(relative(pagesDir, page?.file ?? "").replaceAll("\\", "/"), "harbor/home/+page.svelte")
})

test("dotted path segments stay in the id", () => {
  const pagesDir = writeTree({
    "harbor.home/+page.svelte": "<h1>home</h1>\n",
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.id, "harbor.home")
})

test("(groups) are dropped from the id", () => {
  const pagesDir = writeTree({
    "(app)/harbor/home/+page.svelte": "<h1>home</h1>\n",
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.id, "harbor.home")
})

test("_-prefixed files and dirs are not discovered", () => {
  const pagesDir = writeTree({
    "_skip/+page.svelte": "<h1>skip</h1>\n",
    "harbor/_hidden/+page.svelte": "<h1>hidden</h1>\n",
    "harbor/home/+page.svelte": "<h1>home</h1>\n",
  })
  const pages = svelteFiles().discover(pagesDir)
  assert.deepEqual(
    pages.map((page) => page.id),
    ["harbor.home"],
  )
})

test("+page.ts export const id overrides the path id", () => {
  const pagesDir = writeTree({
    "x/+page.svelte": "<h1>home</h1>\n",
    "x/+page.ts": `export const id = "harbor.home"\n`,
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.id, "harbor.home")
})

test("layout chain is root-first", () => {
  const pagesDir = writeTree({
    "+layout.svelte": "<slot />\n",
    "blog/+layout.svelte": "<slot />\n",
    "blog/home/+page.svelte": "<h1>home</h1>\n",
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.id, "blog.home")
  assert.deepEqual(relLayouts(pagesDir, page?.layouts ?? []), ["+layout.svelte", "blog/+layout.svelte"])
})

test("group layouts still wrap descendant pages", () => {
  const pagesDir = writeTree({
    "+layout.svelte": "<slot />\n",
    "(app)/+layout.svelte": "<slot />\n",
    "(app)/home/+page.svelte": "<h1>home</h1>\n",
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.id, "home")
  assert.deepEqual(relLayouts(pagesDir, page?.layouts ?? []), ["+layout.svelte", "(app)/+layout.svelte"])
})

test("root +page.svelte without an id override throws", () => {
  const pagesDir = writeTree({
    "+page.svelte": "<h1>root</h1>\n",
  })
  assert.throws(() => svelteFiles().discover(pagesDir), /empty page id/)
})

test("duplicate ids throw with both file paths", () => {
  const pagesDir = writeTree({
    "harbor/home/+page.svelte": "<h1>a</h1>\n",
    "harbor.home/+page.svelte": "<h1>b</h1>\n",
  })
  assert.throws(() => svelteFiles().discover(pagesDir), /duplicate page id 'harbor.home'/)
})

test("sibling +head.svelte is compiled into the discovered page head", () => {
  const pagesDir = writeTree({
    "harbor/home/+page.svelte": "<h1>home</h1>\n",
    "harbor/home/+head.svelte":
      '<script lang="ts">let { seed } = $props()</script>\n<title>{seed.data.title} — Harbor</title>\n<meta name="description" content="A board." />\n',
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.head, '<title>{{data.title}} — Harbor</title>\n<meta name="description" content="A board." />')
})

test("pages without +head.svelte have no compiled head", () => {
  const pagesDir = writeTree({
    "harbor/home/+page.svelte": "<h1>home</h1>\n",
  })
  const [page] = svelteFiles().discover(pagesDir)
  assert.equal(page?.head, undefined)
})

test("entrySource imports createPage and lists layouts", () => {
  const source = svelteFiles().entrySource({
    id: "harbor.home",
    file: "/proj/src/pages/harbor/home/+page.svelte",
    layouts: ["/proj/src/pages/+layout.svelte", "/proj/src/pages/harbor/+layout.svelte"],
    head: "<title>Harbor</title>",
  })
  assert.equal(
    source,
    [
      `import Page from "/proj/src/pages/harbor/home/+page.svelte"`,
      `import L0 from "/proj/src/pages/+layout.svelte"`,
      `import L1 from "/proj/src/pages/harbor/+layout.svelte"`,
      `import { createPage } from "@kolektiv/keel-svelte/mount"`,
      `export const { mount, unmount, update } = createPage(Page, [L0, L1])`,
      "",
    ].join("\n"),
  )
})
