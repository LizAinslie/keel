import { writeFileSync } from "node:fs"
import { join } from "node:path"
import { idsFromContract } from "./pack-feb.ts"

export interface ManifestChunk {
  type?: string
  fileName?: string
  name?: string
  isEntry?: boolean
  imports?: string[]
  viteMetadata?: {
    importedCss?: Iterable<string>
  }
}

export type ManifestBundle = Record<string, ManifestChunk>

export interface PackManifestPage {
  module: string
  css: string[]
  head?: string
}

export interface PackManifest {
  format: "keel/1"
  id: string
  version: string
  framework: string
  host: string
  pages: Record<string, PackManifestPage>
  notFound?: string
}

export interface WritePackManifestOptions {
  outDir: string
  id: string
  version: string
  framework: string
  host: string
  pages: Record<string, PackManifestPage>
  notFound?: string
  contract?: string
}

export function cssFromBundle(entryFileName: string, bundle: ManifestBundle): string[] {
  const css: string[] = []
  const seen = new Set<string>()

  function walk(fileName: string): void {
    if (seen.has(fileName)) return
    seen.add(fileName)
    const item = bundle[fileName]
    if (!item || item.type === "asset") return
    const importedCss = item.viteMetadata?.importedCss
    if (importedCss) {
      for (const href of importedCss) css.push(href)
    }
    for (const imported of item.imports ?? []) walk(imported)
  }

  walk(entryFileName)
  return [...new Set(css)]
}

export function writePackManifest(options: WritePackManifestOptions): PackManifest {
  const pageIds = Object.keys(options.pages)
  if (options.contract) {
    const allowed = idsFromContract(options.contract)
    const unknown = pageIds.filter((id) => !allowed.has(id))
    if (unknown.length > 0) {
      throw new Error(`pack implements unknown page id '${unknown.join("', '")}' (not in contract)`)
    }
  }
  const manifest: PackManifest = {
    format: "keel/1",
    id: options.id,
    version: options.version,
    framework: options.framework,
    host: options.host,
    pages: options.pages,
  }
  if (options.notFound) manifest.notFound = options.notFound
  writeFileSync(join(options.outDir, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`)
  return manifest
}
