<script lang="ts">
  import { Head, Link, page } from "@kolektiv/keel-svelte"
  import Shell from "../components/Shell.svelte"
  import type { PostPage } from "../lib/page-types"
  import "../styles.css"

  const ctx = page<PostPage>()
</script>

<Head title={`${ctx.data.title} · Harbor`} />

<Shell>
  <p><Link href="/posts">All entries</Link></p>
  <h1>{ctx.data.title}</h1>
  <p class="meta"><time>{ctx.data.published}</time></p>
  <article class="entry">
    <p>{ctx.data.body}</p>
  </article>
</Shell>
