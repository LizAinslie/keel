<script lang="ts">
  import { Head, Link, page } from "@kolektiv/keel-svelte"
  import Shell from "../components/Shell.svelte"
  import type { HomePage } from "../lib/page-types"
  import "../styles.css"

  const ctx = page<HomePage>()
</script>

<Head title={`${ctx.data.title} · Harbor`} />

<Shell>
  <p class="kicker">{ctx.data.kicker}</p>
  <h1>{ctx.data.title}</h1>
  <p class="lede">{ctx.data.lede}</p>
  <ol class="feed">
    {#each ctx.data.posts as post}
      <li>
        <Link href={`/p/${post.slug}`} prefetch="hover">{post.title}</Link>
        <p class="excerpt">{post.excerpt}</p>
        <p class="meta">{post.published}</p>
      </li>
    {/each}
  </ol>
</Shell>
