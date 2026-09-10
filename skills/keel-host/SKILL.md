---
name: keel-host
description: >
  Implement or extend a Keel host (server). Use when adding pages, actions,
  CSRF, the live pack schema, pack serving, or a non-Ktor backend that speaks
  Keel. Triggers: "keel host", "KeelEngine", "respondPage", "/__keel/schema",
  "@KeelAction", "page registry". Slash: /keel-host.
---

# Keel host

The host owns URLs, page ids, payload types, actions, and which pack to mount.
Packs implement ids — never paths.

Canonical protocol: repo `docs/src/content/docs/implementing/protocol.mdx` and
`host-adapter.mdx`. Kotlin reference: `lib/` (no Ktor) and `ktor/KeelEngine.kt`.

## Do this

1. Register pages with id + absolute path + serializer. Default method is GET;
   POST/PUT/PATCH/DELETE are opt-in. Path grammar: `{name}`, `{name?}` last,
   `{name...}` last. Match by specificity, not registration order.
2. Build a seed for every document and visit (`v`, `page`, `path`, `params`,
   `data`, `errors`, `theme`, `entry`, `css`, `host`). Document GET writes
   `head` into HTML and `modulepreload`s bootstrap + `seed.entry`. Visits
   return JSON when `X-Keel-Visit: true`.
3. Serve **`GET /__keel/schema`** as `keel/1` JSON from the live page and
   action registries (`Typegen.emitJson`). This is how `keel-scaffold` learns
   the contract.
4. Actions: `POST /__keel/action/{id}`, JSON in/out. Validation is 422
   `{ "errors": … }`. Bind request context across coroutine hops
   (`ThreadLocal.asContextElement` on JVM).
5. CSRF on writes: non-simple marker (`application/json` or `X-Keel-Visit`)
   plus same-origin Origin / Sec-Fetch-Site. Session cookies `SameSite=Lax`.
6. Assets at `/__keel/pack/{bundleId}/…`: strong ETag, 304, `immutable` for
   hashed chunks/`assets/`, otherwise `must-revalidate`.

## Do not

- Let packs own URL patterns or fetch a second read model.
- Apply `only`/`except` to document GETs.
- Skip `/__keel/schema` — scaffold and other languages consume it.
