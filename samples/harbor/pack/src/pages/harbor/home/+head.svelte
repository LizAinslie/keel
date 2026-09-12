<script lang="ts">
  import type { KeelSeed } from "@kolektiv/keel"
  import type { HomePage } from "../../../lib/page-types"

  let { seed }: { seed: KeelSeed<HomePage> } = $props()
</script>

<title>Harbor</title>
<meta name="description" content="An in-memory message board. Set a display name and post." />
<meta property="og:title" content="Harbor" />
<meta property="og:type" content="website" />
