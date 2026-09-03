# Keel

This file is for humans and coding agents working in this repository.

Keel is a protocol and libraries for serving MPAs with a frameworked UI from
Kotlin. The **host** owns URLs, page ids, payload types, and which frontend
**pack** to mount. Packs implement ids — never paths. One pack is an app UI.
Many packs are installed themes against the same typed contract.

## Layout

| Path | What |
| --- | --- |
| `lib/` | Kotlin core (`dev.kolektiv.keel:core`) — seed, manifest, `PageRegistry`, theme chain. No Ktor dependency. |
| `ktor/` | `dev.kolektiv.keel:ktor` — binds the registry, serves the HTML shell, visits, pack files. |
| `samples/harbor` | Test host: Ktor + Svelte pack. `./gradlew :samples:harbor:run` → http://127.0.0.1:8090 |
| `packages/core` | `@kolektiv/keel` — visits, history, prefetch |
| `packages/svelte` | `@kolektiv/keel-svelte` — `Link`, `Form`, `Head`, `page()`, `useForm` |
| `docs/` | Homepage and guides (Astro 7, Tailwind 4, daisyUI 5, Shiki Catppuccin). pnpm workspace package `@kolektiv/keel-docs`. |
| `buildSrc/` | Gradle conventions (JVM 17, Maven publish) |
| `public/` | `logo.svg` / `favicon.svg` product mark, `mark.svg` hull glyph |

## Commands

```bash
./gradlew :lib:test :ktor:test
pnpm install
pnpm test && pnpm typecheck
pnpm --filter @kolektiv/keel-docs build
pnpm dev                        # docs, http://127.0.0.1:8080
./gradlew :samples:harbor:run    # sample host, http://127.0.0.1:8090
```

Java 17, Kotlin 2.1, pnpm 9. The JS workspace root is this repository (`packages/*` and `docs`).

## Protocol

- Page ids + kotlinx.serialization payload types **are** the contract. Typegen
  emits TS from the same registry.
- Theme selection is host policy (`ChainThemeResolver`). Packs do not read a
  visitor theme from the browser.
- The seed JSON is the only page payload. Packs must not fetch a second source
  of truth or own URL patterns.
- One pack and many packs use the same host API. A single-pack app is a theme
  chain of one.
- First adapter is Svelte 5. React snippets in the docs are sketched against
  the same router.

## Docs site

Catppuccin daisyUI themes (`catppuccin-mocha` default). Type: Source Serif 4
(display), Source Sans 3 (body / UI), Iosevka (code) in
`docs/src/styles/global.css`. Site chrome uses daisyUI
primitives. Code samples: Svelte first, Ktor host. Framework and TS/JS
selectors live on docs pages only.

On GitHub Pages the site is served under `/keel`. All in-app links must go
through `path()` in `docs/src/lib/paths.ts` (or `import.meta.env.BASE_URL`).
Set `GITHUB_PAGES=1` for that build; local `astro dev` stays at `/`. Astro
emits to `/dist` at the repo root.

## Contributing

Apache-2.0. Keep page ids stable. Do not commit `node_modules/`, `**/build/`,
`.gradle/`, `.env`, or signing material. Match the style of the file you are
in; do not reformat unrelated code or add deps without a need.
