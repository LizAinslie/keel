<script lang="ts">
  import type { KeelSeed } from "@kolektiv/keel"
  import type { NotFoundPage } from "../../../lib/page-types"

  let { seed }: { seed: KeelSeed<NotFoundPage> } = $props()
</script>

<title>Not on this board — Harbor</title>
<meta name="description" content="No page at {seed.path}." />
