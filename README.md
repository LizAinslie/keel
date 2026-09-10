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
- `lib/` — seed, manifest, page registry, `FrontendBundle`, typegen, theme resolver (no Ktor)
- `ktor/` — Ktor plugin: document shell, `respondPage`, visits, pack static files
- `samples/harbor` — in-memory message board (Ktor + Svelte pack; `./gradlew :samples:harbor:run`)
- `packages/` — TypeScript router, Svelte bindings, and `keel-pack` (`.feb` zip + `keel-scaffold`)
- `skills/` — Agent Skills for Claude, Grok, and `.agents/skills`
- `llms.txt` — curated map for LLMs (also served from the docs site)
- `docs/` — homepage and documentation (Astro, Tailwind 4, daisyUI 5, Shiki Catppuccin)
- `public/logo.svg` — product mark (also `favicon.svg`); `public/mark.svg` is the hull glyph

```bash
./gradlew :lib:test :ktor:test :samples:harbor:test
pnpm install
pnpm test
pnpm build:packages
pnpm dev
./gradlew :samples:harbor:run
```

Java 17+, Kotlin 2.2, Gradle 9.5. CI runs JVM tests on JDK 17 / 21 / 25 and
the JS workspace on Node 22. Implementing-Keel guides live under
`docs/src/content/docs/implementing/`.

Consumers resolve **one** Maven repo — `maven-releases` for numbered
versions, or `maven-snapshots` for `*-SNAPSHOT*` — never both. npm is the
same split (`npm-releases` / `npm-snapshots`). Maintainers publish with
one Nexus login to `keel-maven` + `keel-npm` (grouped) plus the matching
releases or snapshots repo. Credentials: GitHub Actions secrets
`YURI_CAPITAL_REPO_USERNAME` / `YURI_CAPITAL_REPO_PASSWORD`, or the same
names as env, or `keel.publishing.yuriCapitalRepoUsername` /
`keel.publishing.yuriCapitalRepoPassword` in `~/.gradle/gradle.properties`.
`.github/workflows/publish.yml`.

The pnpm workspace lives at the repository root (`packages/*` and `docs`).

Docs deploy from `.github/workflows/pages.yml` to GitHub Pages (`/keel`). Enable
**Settings → Pages → Source: GitHub Actions**. Pages on a private repo needs
GitHub Pro; it will serve publicly once the repository is public.
