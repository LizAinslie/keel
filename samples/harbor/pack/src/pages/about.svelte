<script lang="ts">
  import { Head, page } from "@kolektiv/keel-svelte"
  import Shell from "../components/Shell.svelte"
  import type { AboutPage } from "../lib/page-types"
  import "../styles.css"

  const ctx = page<AboutPage>()
</script>

<Head title={`${ctx.data.title} · Harbor`} />

<Shell>
  <h1>{ctx.data.title}</h1>
  <p class="copy">{ctx.data.copy}</p>
  <p class="meta">{ctx.data.pack}</p>
</Shell>
