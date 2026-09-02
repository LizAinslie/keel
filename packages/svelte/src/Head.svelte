<script lang="ts">
  import { setTitle } from "./head.ts"
  import type { Snippet } from "svelte"

  let { title, children }: { title?: string; children?: Snippet } = $props()

  $effect(() => {
    if (title) setTitle(title)
  })
</script>

<svelte:head>
  {#if title}
    <title>{title}</title>
  {/if}
  {#if children}
    {@render children()}
  {/if}
</svelte:head>
