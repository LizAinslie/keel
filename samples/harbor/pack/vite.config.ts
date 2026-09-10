import { svelte } from "@sveltejs/vite-plugin-svelte"
import { keelPack } from "@kolektiv/keel-pack/vite"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [
    svelte(),
    keelPack({
      id: "harbor",
      version: "0.1.0",
      framework: "svelte",
      pagesDir: "src/pages",
      bootstrap: "src/bootstrap.ts",
      contract: "src/lib/page-types.ts",
      notFound: "harbor.notFound",
      pack: "dist/harbor.feb",
    }),
  ],
})
