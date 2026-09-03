<script lang="ts">
  import { Head, Link, page } from "@kolektiv/keel-svelte"
  import { router } from "@kolektiv/keel"
  import Shell from "../components/Shell.svelte"
  import type { ListPage } from "../lib/page-types"
  import "../styles.css"

  const ctx = page<ListPage>()
  let q = $state(ctx.data.query)

  function onSubmit(event: SubmitEvent) {
    event.preventDefault()
    const query = q.trim()
    void router.visit(query ? `/posts?q=${encodeURIComponent(query)}` : "/posts")
  }
</script>

<Head title={`${ctx.data.title} · Harbor`} />

<Shell>
  <h1>{ctx.data.title}</h1>
  <form class="search" onsubmit={onSubmit}>
    <input name="q" bind:value={q} type="search" placeholder="Filter entries" aria-label="Filter entries" />
    <button type="submit">Search</button>
  </form>
  {#if ctx.data.posts.length === 0}
    <p class="lede">Nothing matches.</p>
  {:else}
    <ol class="feed">
      {#each ctx.data.posts as post}
        <li>
          <Link href={`/p/${post.slug}`} prefetch="hover">{post.title}</Link>
          <p class="excerpt">{post.excerpt}</p>
        </li>
      {/each}
    </ol>
  {/if}
</Shell>
