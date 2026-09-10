<script lang="ts">
  import type { Component } from "svelte"
  import Wrap from "./Wrap.svelte"

  let { layouts, Page }: { layouts: Component[]; Page: Component } = $props()
  const Layout = $derived(layouts[0])
  const rest = $derived(layouts.slice(1))
</script>

{#if Layout}
  <Layout>
    <Wrap layouts={rest} {Page} />
  </Layout>
{:else}
  <Page />
{/if}
