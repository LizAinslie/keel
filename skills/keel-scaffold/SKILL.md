---
name: keel-scaffold
description: >
  Scaffold a Keel pack from a running host's live schema. Use when the user
  wants a new pack directory, blank pages for each page id, or
  `keel-scaffold example.com ./pack`. Triggers: "keel-scaffold",
  "/__keel/schema", "scaffold pack", "blank pages". Slash: /keel-scaffold.
---

# keel-scaffold

The host is the source of truth. `GET /__keel/schema` returns `keel/1` JSON
(pages with `type`/`path`/`methods`, actions, types). The CLI writes a blank
Svelte pack that implements those ids.

```
pnpm exec keel-scaffold <origin> <dir>
pnpm exec keel-scaffold --schema schema.json <dir>
pnpm exec keel-scaffold 127.0.0.1:8090 ./pack --id harbor
```

- `localhost` / `127.*` → `http://`. Other hosts → `https://` unless a
  scheme is given.
- `--framework svelte` (default). `--framework react` errors until the
  React adapter ships.
- Refuses to overwrite existing files unless `--force`.

Generated tree: `vite.config.ts` + `keelPack`, `src/bootstrap.ts`,
`src/lib/page-types.ts` + `.json`, `src/pages/{id as dirs}/+page.svelte`
(typed `page<T>()` + JSON dump), `+page.ts` with `export const id`.

After scaffolding: `pnpm install` in a workspace that has `@kolektiv/keel*`
(or change `workspace:*` to published versions), then `pnpm build`.

Do not invent page ids. If the schema is missing a page the host registered,
fix the host and re-fetch `/__keel/schema`.
