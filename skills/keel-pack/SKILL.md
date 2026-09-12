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

## Actions and effects

`useAction(id)` returns a reactive proxy: state reads (`isPending`, `error`)
subscribe the current `$effect` / `$derived` to the mutation, while method
reads (`mutateAsync`, `reset`) do not. An effect that reads state still re-runs
on every mutation transition, so an unguarded load effect that checks
`isPending` fires the action again — an endless `POST /__keel/action/{id}`
loop. `reload: false` skips the visit after success, not the state
subscription.

Rules:

1. Never read action state inside `$effect` / `$derived` tracking scope.
2. Load from event handlers, or wrap all action-proxy access in `untrack(...)`.
3. Guard effect-driven loads by a key so they run once per open/id.
4. `reload: false` skips the visit after success, not the proxy subscription.

```svelte
<!-- WRONG: effect reads isPending, so it re-runs on settle and fires again -->
$effect(() => {
  if (getSharing.isPending) return
  void getSharing.mutateAsync({ id })
})
```

```svelte
<!-- RIGHT: guard by id, keep action proxy access inside untrack -->
import { untrack } from "svelte"

let loadedId: string | undefined

$effect(() => {
  const id = sharingId
  if (!id || loadedId === id) return
  loadedId = id
  untrack(() => {
    getSharing.reset()
    void getSharing.mutateAsync({ id })
  })
})
```

Prefer event handlers for user-initiated writes. `page()` has the same proxy
shape but is read-only, so reading it in an effect cannot start an action.

## Build

`keelPack` in Vite discovers pages, writes `manifest.json`, and zips `.feb`
(`packFeb` default). Custom hosts pass `router` (anything but Svelte) and
optionally `manifest` / `packager` hooks.

`RouterAdapter`: `{ name, discover(pagesDir), entrySource(page) }`.
`svelteFiles()` ships today; React is a later adapter — same `PageModule`.
