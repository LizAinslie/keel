<script lang="ts">
  import { Head, Link, page } from "@kolektiv/keel-svelte"
  import Shell from "../components/Shell.svelte"
  import type { NotFoundPage } from "../lib/page-types"
  import "../styles.css"

  const ctx = page<NotFoundPage>()
</script>

<Head title={`${ctx.data.title} · Harbor`} />

<Shell>
  <h1>{ctx.data.title}</h1>
  <p class="lede">No page at <code>{ctx.data.path}</code>.</p>
  <p><Link href="/">Back to the journal</Link></p>
</Shell>
