# Keel

Host-owned routing for Kotlin MPAs. The **host** owns URLs, page ids, payload
types, and which frontend pack to mount. Packs implement ids. One pack is an
app UI. Many packs are installed themes against the same contract.

## Layout

| Path | What |
| --- | --- |
| `lib/` | Kotlin core (`dev.kolektiv.keel:core`) — seed, manifest, `PageRegistry`, theme chain. No Ktor. |
| `packages/core` | `@kolektiv/keel` — visits, history, prefetch |
| `packages/svelte` | `@kolektiv/keel-svelte` — `Link`, `Form`, `Head`, `page()`, `useForm` |
| `docs/` | Astro 7 + Tailwind 4 + daisyUI 5 + Shiki Catppuccin |
| `buildSrc/` | Gradle conventions (JVM 17, Maven publish) |

## Commands

```bash
./gradlew :lib:test
cd packages && pnpm install && pnpm test && pnpm typecheck
cd docs && npm install && npm run check && npm run build
cd docs && npm run dev   # http://0.0.0.0:8080
```

Java 17. Kotlin 2.1. pnpm 9 for `packages/`. Do not run the sandbox root `package.json` — that is App Builder host, not Keel.

## Design rules

- Page ids + payload types are the contract. Packs never own URL patterns.
- Theme choice is host policy (`ChainThemeResolver`). Packs do not read `localStorage` for theme.
- Seed JSON is the only page payload. Packs do not fetch a second source of truth.
- Same protocol for one pack (glue a Kotlin UI) and many packs (typed blog themes).
- Docs examples: Svelte first, Ktor host. React snippets are sketched.

## Docs UI

Catppuccin daisyUI themes (`catppuccin-mocha` default). Newsreader display, Source Sans body, IBM Plex Mono code. daisyUI for primitives. Site theme is a picker; code theme defaults to follow-site with a Catppuccin override.

Homepage: no App Builder branding language, no global framework bar (docs only). Hero code deck overlays the preview on large screens and stacks on small ones.

## Do not commit

`node_modules/`, `**/build/`, `.gradle/`, `.env`, signing material, App Builder host files (`src/`, `scripts/`, `.grok/`, screenshots). See `.gitignore`.

## Style

Kotlin: existing package layout, kotlinx.serialization, no extra abstraction for one-call sites. TypeScript: ESM, no new deps without need. Do not reformat files you did not change.
