<script lang="ts">
  import type { Component } from "svelte"
  import Wrap from "./Wrap.svelte"
  import { applyHead, fromPageHead } from "./head.js"
  import { page } from "./page.js"

  let {
    layouts,
    Page,
    head = true,
  }: {
    layouts: Component[]
    Page: Component
    head?: boolean
  } = $props()
  const Layout = $derived(layouts[0])
  const rest = $derived(layouts.slice(1))
  const seed = page()

  $effect(() => {
    if (!head) return
    if (!seed.head) return
    return applyHead(fromPageHead(seed.head))
  })
</script>

{#if Layout}
  <Layout>
    <Wrap layouts={rest} {Page} head={false} />
  </Layout>
{:else}
  <Page />
{/if}
