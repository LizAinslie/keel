<script lang="ts">
  import { Link } from "@kolektiv/keel-svelte"
  import type { Snippet } from "svelte"

  let { children }: { children: Snippet } = $props()
</script>

<div class="shell">
  <header>
    <Link href="/" class="wordmark">Harbor</Link>
    <nav>
      <Link href="/" prefetch="hover">Journal</Link>
      <Link href="/posts" prefetch="hover">All</Link>
      <Link href="/about" prefetch="hover">About</Link>
    </nav>
  </header>
  {@render children()}
</div>
