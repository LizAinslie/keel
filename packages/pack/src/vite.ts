import { existsSync } from "node:fs"
import { resolve } from "node:path"
import type { Plugin } from "vite"
import type { DiscoveredPage, RouterAdapter } from "./adapter.ts"
import { cssFromBundle, writePackManifest, type ManifestBundle } from "./manifest-write.ts"
import { packFeb } from "./pack-feb.ts"
import { svelteFiles } from "./svelte.ts"

export interface KeelPackOptions {
  id: string
  version: string
  framework: string
  host?: string
  pagesDir?: string
  bootstrap?: string
  contract?: string
  notFound?: string
  pack?: string
  router?: RouterAdapter
}

const VIRTUAL_PREFIX = "virtual:keel-page/"
const RESOLVED_PREFIX = `\0${VIRTUAL_PREFIX}`

export function keelPack(options: KeelPackOptions): Plugin {
  const host = options.host ?? "#__keel_root"
  const pagesDirOpt = options.pagesDir ?? "src/pages"
  const bootstrapOpt = options.bootstrap ?? "src/bootstrap.ts"
  let root = process.cwd()
  let adapter: RouterAdapter
  let pages: DiscoveredPage[] = []
  let pageById = new Map<string, DiscoveredPage>()

  return {
    name: "keel-pack",
    enforce: "pre",
    config(userConfig) {
      root = userConfig.root ? resolve(userConfig.root) : process.cwd()
      const pagesDir = resolve(root, pagesDirOpt)
      const bootstrap = resolve(root, bootstrapOpt)
      if (!existsSync(pagesDir)) {
        throw new Error(`keelPack: pagesDir not found: ${pagesDir}`)
      }
      if (!existsSync(bootstrap)) {
        throw new Error(`keelPack: bootstrap not found: ${bootstrap}`)
      }
      adapter = options.router ?? defaultRouter(options.framework)
      pages = adapter.discover(pagesDir)
      if (pages.length === 0) {
        throw new Error(`keelPack: no pages found in ${pagesDir}`)
      }
      pageById = new Map(pages.map((page) => [page.id, page]))
      if (options.notFound && !pageById.has(options.notFound)) {
        throw new Error(`keelPack: notFound id '${options.notFound}' was not discovered`)
      }
      const entry: Record<string, string> = { bootstrap }
      for (const page of pages) {
        entry[`pages/${page.id}`] = `${VIRTUAL_PREFIX}${page.id}`
      }
      return {
        appType: "custom",
        publicDir: false,
        // Packs are served as ES modules, not re-bundled. Vite lib mode
        // otherwise leaves `process.env.NODE_ENV` in query-core etc.
        define: {
          "process.env.NODE_ENV": JSON.stringify("production"),
        },
        build: {
          emptyOutDir: true,
          manifest: true,
          cssCodeSplit: true,
          lib: {
            entry,
            formats: ["es"],
          },
          rollupOptions: {
            // lib.entry is path.resolve'd; keep virtual ids as rollup input
            input: entry,
            output: {
              entryFileNames: "[name].js",
              chunkFileNames: "chunks/[name]-[hash].js",
              assetFileNames: "assets/[name][extname]",
            },
          },
        },
      }
    },
    resolveId(id) {
      const pageId = virtualPageId(id)
      if (pageId === undefined) return
      return `${RESOLVED_PREFIX}${pageId}`
    },
    load(id) {
      if (!id.startsWith(RESOLVED_PREFIX)) return
      const pageId = id.slice(RESOLVED_PREFIX.length)
      const page = pageById.get(pageId)
      if (!page) throw new Error(`keelPack: unknown virtual page '${pageId}'`)
      return adapter.entrySource(page)
    },
    writeBundle(outputOptions, bundle) {
      const pagesMap: Record<string, { module: string; css: string[] }> = {}
      const chunks = bundle as ManifestBundle
      for (const page of pages) {
        const name = `pages/${page.id}`
        const chunk = Object.values(bundle).find((item) => {
          if (item.type !== "chunk" || !item.isEntry) return false
          return item.name === name || item.fileName === `${name}.js`
        })
        if (!chunk || chunk.type !== "chunk") {
          throw new Error(`keelPack: missing entry chunk '${name}'`)
        }
        pagesMap[page.id] = {
          module: chunk.fileName,
          css: cssFromBundle(chunk.fileName, chunks),
        }
      }
      const outDir = outputOptions.dir ?? resolve(root, "dist")
      const notFound = options.notFound ? pagesMap[options.notFound]?.module : undefined
      const contract = options.contract ? resolve(root, options.contract) : undefined
      writePackManifest({
        outDir,
        id: options.id,
        version: options.version,
        framework: options.framework,
        host,
        pages: pagesMap,
        notFound,
        contract,
      })
      if (options.pack) {
        packFeb({
          distDir: outDir,
          outFile: resolve(root, options.pack),
          contract,
        })
      }
    },
  }
}

function virtualPageId(id: string): string | undefined {
  const normalized = id.replaceAll("\\", "/")
  const index = normalized.indexOf(VIRTUAL_PREFIX)
  if (index === -1) return undefined
  return normalized.slice(index + VIRTUAL_PREFIX.length)
}

function defaultRouter(framework: string): RouterAdapter {
  if (framework === "svelte") return svelteFiles()
  throw new Error(`keelPack: no router for framework '${framework}'; pass router`)
}
