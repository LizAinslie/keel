<script lang="ts">
  import type { KeelSeed } from "@kolektiv/keel"
  import type { UserPage } from "../../../lib/page-types"

  let { seed }: { seed: KeelSeed<UserPage> } = $props()
</script>

<title>{seed.data.user.displayName} — Harbor</title>
<meta name="description" content="Messages from {seed.data.user.displayName}." />
<meta property="og:title" content="{seed.data.user.displayName} — Harbor" />
