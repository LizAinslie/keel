<script lang="ts">
  import type { Snippet } from "svelte"

  let { children }: { children: Snippet } = $props()
</script>

<p class="kicker">Harbor board</p>
{@render children()}
