# Keel

Host-owned routing and swappable frontend packs for Kotlin servers.

| Coordinate | Name |
| --- | --- |
| Maven | `dev.kolektiv.keel:core` |
| npm router | `@kolektiv/keel` |
| npm Svelte | `@kolektiv/keel-svelte` |
| docs | `@kolektiv/keel-docs` (Astro + daisyUI) |

- `buildSrc/` — Kotlin JVM + Maven publish conventions
- `lib/` — seed, manifest, page registry, theme resolver (no Ktor)
- `packages/` — pnpm workspace for the TypeScript router and Svelte bindings
- `docs/` — homepage and documentation (Astro, Tailwind 4, daisyUI 5, Shiki Catppuccin Mocha)
