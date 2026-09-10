import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { mkdirSync } from "node:fs"
import { zipSync } from "fflate"

export interface PackFebOptions {
  distDir: string
  outFile: string
  contract?: string
}

export function packFeb(options: PackFebOptions): void {
  const distDir = options.distDir
  if (!existsSync(distDir) || !statSync(distDir).isDirectory()) {
    throw new Error(`pack dist is not a directory: ${distDir}`)
  }
  const manifestPath = join(distDir, "manifest.json")
  if (!existsSync(manifestPath)) {
    throw new Error(`missing manifest.json in ${distDir}`)
  }
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8")) as {
    pages?: Record<string, unknown>
  }
  const implemented = Object.keys(manifest.pages ?? {})
  if (options.contract) {
    const allowed = idsFromContract(options.contract)
    const unknown = implemented.filter((id) => !allowed.has(id))
    if (unknown.length > 0) {
      throw new Error(`pack implements unknown page id '${unknown.join("', '")}' (not in contract)`)
    }
  }
  const files: Record<string, Uint8Array> = {}
  collectFiles(distDir, "", files, options.outFile)
  if (!("manifest.json" in files)) {
    throw new Error("zip is missing manifest.json at the archive root")
  }
  const zipped = zipSync(files, { level: 6 })
  mkdirSync(dirname(options.outFile), { recursive: true })
  writeFileSync(options.outFile, zipped)
}

function collectFiles(dir: string, prefix: string, files: Record<string, Uint8Array>, outFile: string): void {
  for (const name of readdirSync(dir)) {
    if (name === ".vite") continue
    const full = join(dir, name)
    const rel = prefix ? `${prefix}/${name}` : name
    if (statSync(full).isDirectory()) {
      collectFiles(full, rel, files, outFile)
      continue
    }
    if (name.endsWith(".feb")) continue
    if (sameFile(full, outFile)) continue
    files[rel.replaceAll("\\", "/")] = new Uint8Array(readFileSync(full))
  }
}

function sameFile(a: string, b: string): boolean {
  try {
    return relative(a, b) === ""
  } catch {
    return false
  }
}

export function idsFromContract(contractPath: string): Set<string> {
  if (!existsSync(contractPath)) {
    throw new Error(`contract file not found: ${contractPath}`)
  }
  const source = readFileSync(contractPath, "utf8")
  if (contractPath.endsWith(".json")) {
    const parsed = JSON.parse(source) as unknown
    if (Array.isArray(parsed)) return new Set(parsed.map(String))
    if (parsed && typeof parsed === "object") return new Set(Object.keys(parsed as Record<string, unknown>))
    throw new Error(`contract JSON must be an id array or object: ${contractPath}`)
  }
  const block = source.match(/export interface \w*Pages \{([^}]+)\}/)
  if (!block) {
    throw new Error(`no Pages interface in contract: ${contractPath}`)
  }
  return new Set([...block[1].matchAll(/"([^"]+)"\s*:/g)].map((match) => match[1]))
}
