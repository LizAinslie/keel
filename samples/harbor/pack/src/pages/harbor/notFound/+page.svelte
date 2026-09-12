<script lang="ts">
  import { Link, page } from "@kolektiv/keel-svelte"
  import type { NotFoundPage } from "../../../lib/page-types"

  const ctx = page<NotFoundPage>()
</script>

<h1>{ctx.data.title}</h1>
<p class="lede">No page at <code>{ctx.data.path}</code>.</p>
<p><Link href="/">Back to the board</Link></p>
