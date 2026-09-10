# Keel

This file is for humans and coding agents working in this repository.

Keel is a protocol and libraries for serving MPAs with a frameworked UI from
Kotlin. The **host** owns URLs, page ids, payload types, and which frontend
**pack** to mount. Packs implement ids — never paths. One pack is an app UI.
Many packs are installed themes against the same typed contract.

## Layout

| Path | What |
| --- | --- |
| `lib/` | Kotlin core (`dev.kolektiv.keel:core`) — seed, manifest, `PageRegistry`, `ActionRegistry`, `FrontendBundle`, typegen, theme chain. No Ktor dependency. |
| `ktor/` | `dev.kolektiv.keel:ktor` — binds the registry, `respondPage`, actions, HTML shell, visits, pack files. |
| `samples/harbor` | In-memory message board: Ktor + Svelte pack. `./gradlew :samples:harbor:run` → http://127.0.0.1:8090 |
| `packages/core` | `@kolektiv/keel` — visits, history, prefetch, `action()` |
| `packages/svelte` | `@kolektiv/keel-svelte` — `Link`, `Form`, `Head`, `page()`, `useForm`, `useAction` |
| `packages/pack` | `@kolektiv/keel-pack` — `.feb` zip, Vite plugin, `keel-scaffold` |
| `skills/` | Agent Skills (`keel-host`, `keel-pack`, `keel-scaffold`) for Claude / Grok / `.agents` |
| `docs/` | Homepage and guides (Astro 7, Tailwind 4, daisyUI 5, Shiki Catppuccin). pnpm workspace package `@kolektiv/keel-docs`. Getting started splits **Server Installation** and **Client Setup**. |
| `buildSrc/` | Gradle conventions (JVM 17, Maven publish) |
| `public/` | `logo.svg` / `favicon.svg` product mark, `mark.svg` hull glyph. `.idea/icon.svg` is the same mark as the IntelliJ project icon. |

## Commands

```bash
./gradlew :lib:test :ktor:test :samples:harbor:test
./gradlew :samples:harbor:generateKeelTypes
pnpm install
pnpm test && pnpm typecheck
pnpm build:packages
pnpm --filter @kolektiv/keel-docs build
pnpm --filter @kolektiv/harbor-pack build
pnpm exec keel-scaffold 127.0.0.1:8090 ./pack
pnpm dev                        # docs, http://127.0.0.1:8080
./gradlew :samples:harbor:run    # sample host, http://127.0.0.1:8090
```

Java 17 (toolchain; the daemon also runs on 21 and 25), Kotlin 2.2, Gradle
9.5, pnpm 9. `pnpm build:packages` emits `dist/` for the npm packages. The
JS workspace root is this repository (`packages/*` and `docs`).

## Protocol

- Page ids + kotlinx.serialization payload types **are** the contract. A live
  host serves that contract at `GET /__keel/schema`. `keel-scaffold <origin>
  <dir>` writes a blank Svelte pack from it. Gradle `generateKeelTypes` is the
  offline classpath scan. Actions are a Kotlin function `(In) -> Out`.
- Packs ship as `.feb` (zip of `manifest.json` + modules). Hosts load a
  `FrontendBundle` from a jar resource, a file, or an exploded directory.
- Visits hit the real page URL with `X-Keel-Visit: true`. `/__keel/navigate`
  is a compatibility proxy for the pages DSL only.
- Actions POST `/__keel/action/{id}` with JSON. They are **writes**. Reads stay
  the seed. After a mutation, TanStack Query invalidates and a visit rehydrates.
- Document GET writes `seed.head` (`<title>`, description, canonical, `og:*`)
  into the HTML shell. Visits return JSON and are not the SEO unit.
- Theme selection is host policy (`ChainThemeResolver`). Packs do not read a
  visitor theme from the browser.
- The seed JSON is the only **read** model. Packs must not fetch a second
  source of truth or own URL patterns.
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

## Publishing

Maven (`./gradlew :lib:publish :ktor:publish`) always goes to
`https://repo.yuri.capital/repository/keel-maven/`. Versions that contain
`SNAPSHOT` also go to `…/maven-snapshots/`; otherwise also to
`…/maven-releases/`.

npm (`bash .github/scripts/publish-npm.sh`) always goes to
`https://repo.yuri.capital/repository/keel-npm/`, plus `npm-snapshots` or
`npm-releases`. Workflow: `.github/workflows/publish.yml` (tag `v*` or
`workflow_dispatch`).

**Credentials (never commit values).** Same Nexus login for Maven and npm:

1. **CI** — GitHub repo **Settings → Secrets and variables → Actions**:
   `YURI_CAPITAL_REPO_USERNAME`, `YURI_CAPITAL_REPO_PASSWORD`.
2. **Local Maven** — `~/.gradle/gradle.properties`:
   `keel.publishing.yuriCapitalRepoUsername` /
   `keel.publishing.yuriCapitalRepoPassword`, or the same names as env.
3. **Local npm** — those env vars are enough for `publish-npm.sh`. Optional
   user `~/.npmrc` (publish only; consumers do not need auth):

```
@kolektiv:registry=https://repo.yuri.capital/repository/keel-npm/
//repo.yuri.capital/repository/keel-npm/:_auth=<base64 of user:password>
//repo.yuri.capital/repository/keel-npm/:always-auth=true
```

Consumers add **either** the releases repo **or** the snapshots repo, not
both, and not the grouped `keel-maven` / `keel-npm` URLs.
