import { existsSync, readdirSync, readFileSync, statSync } from "node:fs"
import { basename, dirname, join, relative } from "node:path"
import type { DiscoveredPage, RouterAdapter } from "./adapter.ts"

const PAGE_FILE = "+page.svelte"
const PAGE_ID_FILE = "+page.ts"
const LAYOUT_FILE = "+layout.svelte"
const ID_OVERRIDE = /export\s+const\s+id\s*=\s*["']([^"']+)["']/

export function svelteFiles(): RouterAdapter {
  return {
    name: "svelte",
    discover(pagesDir: string): DiscoveredPage[] {
      if (!existsSync(pagesDir) || !statSync(pagesDir).isDirectory()) {
        throw new Error(`pagesDir is not a directory: ${pagesDir}`)
      }
      const files = listFiles(pagesDir)
      const pages: DiscoveredPage[] = []
      const seen = new Map<string, string>()
      for (const file of files) {
        if (basename(file) !== PAGE_FILE) continue
        const id = pageId(pagesDir, file)
        const previous = seen.get(id)
        if (previous) {
          throw new Error(`duplicate page id '${id}' from ${previous} and ${file}`)
        }
        seen.set(id, file)
        pages.push({ id, file, layouts: layoutsFor(pagesDir, file) })
      }
      pages.sort((a, b) => a.id.localeCompare(b.id))
      return pages
    },
    entrySource(page: DiscoveredPage): string {
      const lines = [`import Page from ${JSON.stringify(page.file)}`]
      page.layouts.forEach((layout, index) => {
        lines.push(`import L${index} from ${JSON.stringify(layout)}`)
      })
      const layoutList = page.layouts.map((_, index) => `L${index}`).join(", ")
      lines.push(`import { createPage } from "@kolektiv/keel-svelte/mount"`)
      lines.push(`export const { mount, unmount, update } = createPage(Page, [${layoutList}])`)
      return `${lines.join("\n")}\n`
    },
  }
}

function listFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith("_")) continue
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      out.push(...listFiles(full))
      continue
    }
    if (entry.isFile()) out.push(full)
  }
  return out
}

function pageId(pagesDir: string, file: string): string {
  const override = idOverride(join(dirname(file), PAGE_ID_FILE))
  if (override !== undefined) {
    if (override === "") {
      throw new Error(`empty page id override in ${join(dirname(file), PAGE_ID_FILE)}`)
    }
    return override
  }
  const rel = relative(pagesDir, file).replaceAll("\\", "/")
  const segments = rel.split("/").slice(0, -1).filter((segment) => !isGroup(segment))
  const id = segments.join(".")
  if (id === "") {
    throw new Error(`root +page.svelte has an empty page id; export const id in a sibling +page.ts (${file})`)
  }
  return id
}

function idOverride(file: string): string | undefined {
  if (!existsSync(file)) return undefined
  const match = readFileSync(file, "utf8").match(ID_OVERRIDE)
  return match?.[1]
}

function layoutsFor(pagesDir: string, file: string): string[] {
  const relDir = relative(pagesDir, dirname(file)).replaceAll("\\", "/")
  const segments = relDir === "" || relDir === "." ? [] : relDir.split("/")
  const layouts: string[] = []
  let current = pagesDir
  const rootLayout = join(current, LAYOUT_FILE)
  if (existsSync(rootLayout)) layouts.push(rootLayout)
  for (const segment of segments) {
    current = join(current, segment)
    const layout = join(current, LAYOUT_FILE)
    if (existsSync(layout)) layouts.push(layout)
  }
  return layouts
}

function isGroup(segment: string): boolean {
  return segment.startsWith("(") && segment.endsWith(")")
}
