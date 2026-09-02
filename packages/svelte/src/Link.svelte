<script lang="ts">
  import { router, type Method, type PrefetchMode, type VisitOptions } from "@kolektiv/keel"
  import type { HTMLAnchorAttributes } from "svelte/elements"
  import type { Snippet } from "svelte"

  let {
    href,
    method = "get",
    data,
    replace = false,
    preserveScroll = false,
    preserveState = false,
    prefetch = false,
    only,
    except,
    headers,
    class: className = "",
    children,
    ...rest
  }: HTMLAnchorAttributes &
    VisitOptions & {
      href: string
      prefetch?: PrefetchMode
      class?: string
      children: Snippet
    } = $props()

  function intercept(event: MouseEvent): boolean {
    if (event.defaultPrevented || event.button !== 0) return false
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return false
    return true
  }

  function onClick(event: MouseEvent) {
    if (!intercept(event)) return
    event.preventDefault()
    void router.visit(href, {
      method: method as Method,
      data,
      replace,
      preserveScroll,
      preserveState,
      only,
      except,
      headers,
    })
  }

  function onPointerEnter() {
    if (prefetch === true || prefetch === "hover") void router.prefetch(href)
  }

  function onPointerDown() {
    if (prefetch === "mousedown") void router.prefetch(href)
  }

  $effect(() => {
    if (prefetch === "mount") void router.prefetch(href)
  })
</script>

{#if method === "get"}
  <a
    {href}
    class={className}
    onclick={onClick}
    onpointerenter={onPointerEnter}
    onpointerdown={onPointerDown}
    {...rest}
  >
    {@render children()}
  </a>
{:else}
  <button type="button" class={className} onclick={onClick} {...rest}>
    {@render children()}
  </button>
{/if}
