<script lang="ts">
  import { Head, Link, page } from "@kolektiv/keel-svelte"
  import type { UserPage } from "../../../lib/page-types"

  const ctx = page<UserPage>()
</script>

<Head />

<h1>{ctx.data.user.displayName}</h1>
<p class="lede">Messages from {ctx.data.user.displayName}.</p>
{#if ctx.data.messages.length === 0}
  <p class="lede">No messages yet.</p>
{:else}
  <ol class="feed">
    {#each ctx.data.messages as item (item.id)}
      <li>
        <p class="excerpt">{item.body}</p>
        <p class="meta">{item.at}</p>
      </li>
    {/each}
  </ol>
{/if}
<p><Link href="/">Back to the board</Link></p>
