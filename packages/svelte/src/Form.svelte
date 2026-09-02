<script lang="ts">
  import { router, type Method, type VisitOptions } from "@kolektiv/keel"
  import type { HTMLFormAttributes } from "svelte/elements"
  import type { Snippet } from "svelte"

  let {
    action,
    method = "post",
    preserveScroll = false,
    preserveState = true,
    replace = false,
    resetOnSuccess = false,
    headers,
    class: className = "",
    children,
    ...rest
  }: HTMLFormAttributes &
    VisitOptions & {
      action: string
      resetOnSuccess?: boolean
      class?: string
      children: Snippet
    } = $props()

  function onSubmit(event: SubmitEvent) {
    event.preventDefault()
    const form = event.currentTarget as HTMLFormElement
    const data = new FormData(form)
    void router.visit(action, {
      method: (method as string).toLowerCase() as Method,
      data,
      preserveScroll,
      preserveState,
      replace,
      headers,
      onSuccess() {
        if (resetOnSuccess) form.reset()
      },
    })
  }
</script>

<form {action} method={method as string} class={className} onsubmit={onSubmit} {...rest}>
  {@render children()}
</form>
