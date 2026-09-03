# Keel

Host-owned routing and swappable frontend packs for Kotlin servers.

| Coordinate | Name |
| --- | --- |
| Maven | `dev.kolektiv.keel:core`, `dev.kolektiv.keel:ktor` |
| npm router | `@kolektiv/keel` |
| npm Svelte | `@kolektiv/keel-svelte` |
| docs | [lizainslie.github.io/keel](https://lizainslie.github.io/keel/) |

Agents (human or otherwise) should read [AGENTS.md](./AGENTS.md).

- `buildSrc/` — Kotlin JVM + Maven publish conventions
- `lib/` — seed, manifest, page registry, theme resolver (no Ktor)
- `ktor/` — Ktor plugin: document shell, `/__keel/navigate`, pack static files
- `samples/harbor` — test host + Svelte pack (`./gradlew :samples:harbor:run`)
- `packages/` — TypeScript router and Svelte bindings (`@kolektiv/keel`, `@kolektiv/keel-svelte`)
- `docs/` — homepage and documentation (Astro, Tailwind 4, daisyUI 5, Shiki Catppuccin)
- `public/logo.svg` — product mark (also `favicon.svg`); `public/mark.svg` is the hull glyph

```bash
./gradlew :lib:test :ktor:test
pnpm install
pnpm test
pnpm dev
./gradlew :samples:harbor:run
```

The pnpm workspace lives at the repository root (`packages/*` and `docs`).

Docs deploy from `.github/workflows/pages.yml` to GitHub Pages (`/keel`). Enable
**Settings → Pages → Source: GitHub Actions**. Pages on a private repo needs
GitHub Pro; it will serve publicly once the repository is public.
