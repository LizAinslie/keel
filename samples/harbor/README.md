# Harbor

Test host for Keel — an in-memory **message board**, not a product. A Ktor
process binds the page registry and server actions; a Svelte pack implements
the ids. One pack, so the theme chain is length one.

The seed is the only **read** model. Actions are **writes**. After a mutation,
TanStack Query invalidates and `router.reload()` rehydrates from the host.

```bash
pnpm install
pnpm --filter @kolektiv/harbor-pack build
./gradlew :samples:harbor:run
```

`run` builds the pack first. Open [http://127.0.0.1:8090](http://127.0.0.1:8090).

| Id | Path | Payload |
| --- | --- | --- |
| `harbor.home` | `/` | viewer, feed |
| `harbor.user` | `/u/{id}` | user, messages |
| `harbor.notFound` | unmatched | path |

| Action | Input |
| --- | --- |
| `harbor.setName` | `{ displayName }` — cookie `harbor_uid` |
| `harbor.postMessage` | `{ body }` — requires the cookie |

Document GET writes `<title>` / description / `og:*` from `seed.head` so
crawlers that do not run JS still see the board. Visits (`X-Keel-Visit`)
return JSON; they are not the SEO unit. Pack files are ids, not URLs.

Visits hit the page URL with `X-Keel-Visit: true` (`/__keel/navigate?to=` is
still a proxy). Actions POST JSON to `/__keel/action/{id}`. Pack files are
served from `/__keel/pack/harbor/`. `run` loads `keel/harbor.feb` from the
classpath; pass a dist directory or `.feb` file to override.
