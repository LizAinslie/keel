---
name: keel-pack
description: >
  Author or adapt a Keel frontend pack. Use when writing Svelte pages, a
  RouterAdapter, keelPack Vite config, .feb zips, or a React adapter.
  Triggers: "keel pack", "+page.svelte", "keelPack", "PageModule", ".feb",
  "svelteFiles". Slash: /keel-pack.
---

# Keel pack

A pack implements page ids. It never owns paths. Spec:
`docs/src/content/docs/implementing/pack-adapter.mdx`.

## Page modules

Every compiled page exports `mount` / `unmount` / optional `update`.
`@kolektiv/keel-svelte/mount` `createPage` wraps `+page.svelte` + layouts.

Svelte files live under `pagesDir` as `+page.svelte`. Id is the directory
path with `/` → `.` (`pages/harbor/home/+page.svelte` → `harbor.home`). A
sibling `+page.ts` may `export const id = "harbor.home"`.

## Contract

Import payload types from typegen (`src/lib/page-types.ts`) or from
`GET /__keel/schema` via `keel-scaffold`. Point `keelPack({ contract })` at
the `keel/1` JSON so unknown ids fail the build.

Reads come from `page<T>()`. Writes go through `action()` / `useAction(id)`,
then a visit rehydrates. Do not `fetch` another JSON API for page data.

## Build

`keelPack` in Vite discovers pages, writes `manifest.json`, and zips `.feb`
(`packFeb` default). Custom hosts pass `router` (anything but Svelte) and
optionally `manifest` / `packager` hooks.

`RouterAdapter`: `{ name, discover(pagesDir), entrySource(page) }`.
`svelteFiles()` ships today; React is a later adapter — same `PageModule`.
