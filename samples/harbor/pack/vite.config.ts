import { svelte } from "@sveltejs/vite-plugin-svelte"
import { resolve } from "node:path"
import { defineConfig } from "vite"

const root = resolve(import.meta.dirname, "src")

export default defineConfig({
  plugins: [svelte()],
  appType: "custom",
  publicDir: false,
  build: {
    outDir: "dist",
    emptyOutDir: true,
    manifest: true,
    cssCodeSplit: true,
    lib: {
      entry: {
        bootstrap: resolve(root, "bootstrap.ts"),
        "pages/home": resolve(root, "pages/home.ts"),
        "pages/list": resolve(root, "pages/list.ts"),
        "pages/post": resolve(root, "pages/post.ts"),
        "pages/about": resolve(root, "pages/about.ts"),
        "pages/not-found": resolve(root, "pages/not-found.ts"),
      },
      formats: ["es"],
    },
    rollupOptions: {
      output: {
        entryFileNames: "[name].js",
        chunkFileNames: "chunks/[name]-[hash].js",
        assetFileNames: "assets/[name][extname]",
      },
    },
  },
})
