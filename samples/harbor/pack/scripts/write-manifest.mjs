import { readFileSync, writeFileSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const pack = resolve(dirname(fileURLToPath(import.meta.url)), "..")
const dist = resolve(pack, "dist")
const viteManifest = JSON.parse(readFileSync(resolve(dist, ".vite/manifest.json"), "utf8"))

function entry(source) {
  const item = viteManifest[source]
  if (!item) throw new Error(`vite manifest missing ${source}`)
  const css = [...(item.css ?? [])]
  if (item.imports) {
    for (const key of item.imports) {
      const chunk = viteManifest[key]
      if (chunk?.css) css.push(...chunk.css)
    }
  }
  return { module: item.file, css: [...new Set(css)] }
}

const pages = {
  "harbor.home": entry("src/pages/home.ts"),
  "harbor.list": entry("src/pages/list.ts"),
  "harbor.post": entry("src/pages/post.ts"),
  "harbor.about": entry("src/pages/about.ts"),
  "harbor.notFound": entry("src/pages/not-found.ts"),
}

const manifest = {
  format: "keel/1",
  id: "harbor",
  version: "0.1.0",
  framework: "svelte",
  host: "#__keel_root",
  pages,
  notFound: pages["harbor.notFound"].module,
}

writeFileSync(resolve(dist, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n")
console.log("wrote dist/manifest.json")
