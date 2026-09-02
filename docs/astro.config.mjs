// @ts-check
import { existsSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { defineConfig } from "astro/config"
import mdx from "@astrojs/mdx"
import vercel from "@astrojs/vercel"
import tailwindcss from "@tailwindcss/vite"

const sandboxPlugin = fileURLToPath(new URL("../scripts/grok-pwa-plugin.mjs", import.meta.url))
/** @type {import("vite").Plugin[]} */
const extraVitePlugins = []
if (existsSync(sandboxPlugin)) {
  const { grokPwaPlugin } = await import("../scripts/grok-pwa-plugin.mjs")
  const { appEnvPlugin } = await import("../scripts/app-env-plugin.mjs")
  extraVitePlugins.push(grokPwaPlugin(), appEnvPlugin())
}

export default defineConfig({
  output: "static",
  adapter: vercel(),
  publicDir: "../public",
  srcDir: "./src",
  integrations: [mdx()],
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
