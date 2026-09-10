<script lang="ts">
  import { applyHead } from "./head.ts"
  import { page } from "./page.ts"
  import type { Snippet } from "svelte"

  let {
    title,
    description,
    canonical,
    image,
    type,
    children,
  }: {
    title?: string
    description?: string
    canonical?: string
    image?: string
    type?: string
    children?: Snippet
  } = $props()

  const ctx = page()
  const resolved = $derived({
    title: title ?? ctx.head?.title,
    description: description ?? ctx.head?.description,
    canonical: canonical ?? ctx.head?.canonical,
    image: image ?? ctx.head?.image,
    type: type ?? ctx.head?.type,
  })

  $effect(() => {
    return applyHead(resolved)
  })
</script>

<svelte:head>
  {#if children}
    {@render children()}
  {/if}
</svelte:head>
