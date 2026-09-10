<script lang="ts">
  import { Link, page } from "@kolektiv/keel-svelte"
  import type { Snippet } from "svelte"
  import "../styles.css"

  let { children }: { children: Snippet } = $props()
  const ctx = page()
  const viewer = $derived(ctx.shared?.viewer as { id: string; displayName: string } | undefined)
</script>

<div class="shell">
  <header>
    <div>
      <Link href="/" class="wordmark">Harbor</Link>
      <span class="meta">{ctx.shared?.site}</span>
    </div>
    <nav>
      <Link href="/" prefetch="hover">Board</Link>
      {#if viewer}
        <Link href={`/u/${viewer.id}`} prefetch="hover">{viewer.displayName}</Link>
      {/if}
    </nav>
  </header>
  {@render children()}
</div>
