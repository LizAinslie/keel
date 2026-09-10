// @ts-check
import { copyFileSync, existsSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { defineConfig } from "astro/config"
import mdx from "@astrojs/mdx"
import tailwindcss from "@tailwindcss/vite"

const sandboxPlugin = fileURLToPath(new URL("../scripts/grok-pwa-plugin.mjs", import.meta.url))
/** @type {any[]} */
const extraVitePlugins = []
if (existsSync(sandboxPlugin)) {
  const [{ grokPwaPlugin }, { appEnvPlugin }] = await Promise.all([
    import(sandboxPlugin),
    import(fileURLToPath(new URL("../scripts/app-env-plugin.mjs", import.meta.url))),
  ])
  extraVitePlugins.push(grokPwaPlugin(), appEnvPlugin())
}

const githubPages = process.env.GITHUB_PAGES === "1"
const siteBase = githubPages ? "/keel" : "/"
const llmsTxt = fileURLToPath(new URL("../llms.txt", import.meta.url))

/** Canonical file is repo-root `llms.txt`. Copy a real file into dist so Pages is not a dangling symlink. */
function llmsTxtIntegration() {
  return {
    name: "keel-llms-txt",
    hooks: {
      /** @param {{ dir: URL }} ctx */
      "astro:build:done": ({ dir }) => {
        const base = dir.href.endsWith("/") ? dir : new URL(`${dir.href}/`)
        copyFileSync(llmsTxt, fileURLToPath(new URL("llms.txt", base)))
      },
    },
  }
}

export default defineConfig({
  site: githubPages ? "https://lizainslie.github.io" : "http://localhost:8080",
  base: siteBase,
  output: "static",
  redirects: {
    "/docs/getting-started/install": "/docs/getting-started/server",
  },
  outDir: "../dist",
  publicDir: "../public",
  srcDir: "./src",
  integrations: [mdx(), llmsTxtIntegration()],
  markdown: {
    shikiConfig: {
      themes: {
        mocha: "catppuccin-mocha",
        macchiato: "catppuccin-macchiato",
        frappe: "catppuccin-frappe",
        latte: "catppuccin-latte",
      },
      defaultColor: false,
      wrap: true,
    },
  },
  vite: {
    plugins: [tailwindcss(), ...extraVitePlugins],
    server: {
      host: "0.0.0.0",
      port: 8080,
      strictPort: true,
    },
    preview: {
      host: "127.0.0.1",
      port: 8081,
      strictPort: true,
    },
  },
})
