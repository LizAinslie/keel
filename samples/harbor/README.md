# Harbor

Test host for Keel. A Ktor process binds the page registry; a Svelte pack
implements the ids. One pack, so the theme chain is length one.

```bash
pnpm install
pnpm --filter @kolektiv/harbor-pack build
./gradlew :samples:harbor:run
```

`run` builds the pack first. Open [http://127.0.0.1:8090](http://127.0.0.1:8090).

| Id | Path |
| --- | --- |
| `harbor.home` | `/` |
| `harbor.list` | `/posts` |
| `harbor.post` | `/p/{slug}` |
| `harbor.about` | `/about` |
| `harbor.notFound` | unmatched paths |

Visits go to `/__keel/navigate?to=…`. Pack files are served from `/__keel/pack/`.
