<script lang="ts">
  import { ActionError, Head, Link, page, useAction } from "@kolektiv/keel-svelte"
  import type { HomePage, PostMessageIn, PostMessageOut, SetNameIn, SetNameOut } from "../../../lib/page-types"

  const ctx = page<HomePage>()
  const setName = useAction<SetNameIn, SetNameOut>("harbor.setName")
  const postMessage = useAction<PostMessageIn, PostMessageOut>("harbor.postMessage")

  let displayName = $state(ctx.data.viewer?.displayName ?? "")
  let body = $state("")

  $effect(() => {
    const name = ctx.data.viewer?.displayName
    if (name) displayName = name
  })

  function fieldError(error: unknown, field: string): string | undefined {
    if (error instanceof ActionError) return error.errors[field]?.join(" ")
    return undefined
  }

  function onSetName(event: SubmitEvent) {
    event.preventDefault()
    void setName.mutateAsync({ displayName }).catch(() => undefined)
  }

  function onPost(event: SubmitEvent) {
    event.preventDefault()
    void postMessage
      .mutateAsync({ body })
      .then(() => {
        body = ""
      })
      .catch(() => undefined)
  }
</script>

<Head />

<h1>Harbor</h1>
<p class="lede">A message board. The seed is the read model. Actions write; visits rehydrate.</p>

<form class="stack" onsubmit={onSetName}>
  <div class="field">
    <label for="displayName">Display name</label>
    <input id="displayName" name="displayName" bind:value={displayName} autocomplete="nickname" maxlength="40" />
    {#if fieldError(setName.error, "displayName")}
      <p class="error">{fieldError(setName.error, "displayName")}</p>
    {/if}
  </div>
  <button type="submit" disabled={setName.isPending}>
    {setName.isPending ? "Saving…" : ctx.data.viewer ? "Update name" : "Set name"}
  </button>
</form>

{#if ctx.data.viewer}
  <p class="meta">Posting as {ctx.data.viewer.displayName}</p>
  <form class="stack" onsubmit={onPost}>
    <div class="field">
      <label for="body">Message</label>
      <textarea id="body" name="body" bind:value={body} rows="4" maxlength="2000"></textarea>
      {#if fieldError(postMessage.error, "body")}
        <p class="error">{fieldError(postMessage.error, "body")}</p>
      {/if}
    </div>
    <button type="submit" disabled={postMessage.isPending}>
      {postMessage.isPending ? "Posting…" : "Post"}
    </button>
  </form>
{/if}

<h2>Recent</h2>
{#if ctx.data.feed.length === 0}
  <p class="lede">No messages yet. Set a display name and write the first one.</p>
{:else}
  <ol class="feed">
    {#each ctx.data.feed as item (item.id)}
      <li>
        <Link href={`/u/${item.userId}`} prefetch="hover">{item.displayName}</Link>
        <p class="excerpt">{item.body}</p>
        <p class="meta">{item.at}</p>
      </li>
    {/each}
  </ol>
{/if}
