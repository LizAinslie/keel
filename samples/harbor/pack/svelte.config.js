/** @type {import('@sveltejs/vite-plugin-svelte').Options} */
export default {
  compilerOptions: {
    runes: true,
  },
  vitePlugin: {
    dynamicCompileOptions({ filename }) {
      if (filename.includes("node_modules")) {
        return { runes: false }
      }
    },
  },
}
