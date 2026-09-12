import { action, ActionError, getPage, router, type KeelSeed } from "@kolektiv/keel"
import { createMutation, createQuery, QueryClient } from "@tanstack/svelte-query"
import { get } from "svelte/store"
import { reactiveStore } from "./reactiveStore.js"

let client: QueryClient | null = null

export function getQueryClient(): QueryClient {
  if (!client) {
    client = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: Infinity,
        },
      },
    })
  }
  return client
}

export function pageQueryKey(path: string) {
  return ["keel", "page", path] as const
}

export function hydrateKeelQuery(seed: KeelSeed): void {
  const qc = getQueryClient()
  qc.setQueryData(["keel", "page"], seed)
  qc.setQueryData(pageQueryKey(seed.path), seed)
}

/**
 * Options for {@link useAction}.
 *
 * The proxy returned by {@link useAction} subscribes the calling reactive
 * scope on state reads (`isPending`, `error`, …) but not on method reads
 * (`mutateAsync`, `reset`, …). An effect that reads action state re-runs on
 * every mutation transition; call actions from event handlers or wrap proxy
 * access in `untrack(...)` to avoid re-firing the action (issue #15).
 */
export interface UseActionOptions {
  /** Visit the current URL after a successful action. Default true. */
  reload?: boolean
  preserveScroll?: boolean
  preserveState?: boolean
}

/**
 * Typed POST to `/__keel/action/{id}`. On success the current page is
 * re-visited so `page()` and the TanStack cache rehydrate from the host.
 *
 * Returns a reactive proxy over the mutation:
 *
 * - State reads (`isPending`, `error`, …) subscribe the current reactive
 *   scope (an `$effect` or `$derived`) to the mutation store.
 * - Method reads (`mutateAsync`, `reset`, …) do **not** subscribe — the
 *   function is returned bound to the current store value.
 *
 * Calling a method from an `$effect` therefore does not subscribe the effect,
 * which fixes the self-retriggering action loop from issue #15. Reading state
 * there still can: prefer event handlers for writes, or guard effect-driven
 * loads by a key and keep proxy access inside `untrack(...)`.
 */
export function useAction<I, O>(id: string, options: UseActionOptions = {}) {
  const queryClient = getQueryClient()
  const mutation = createMutation<O, ActionError, I>(
    {
      mutationFn: (input: I) => action<I, O>(id, input),
      onSuccess: async () => {
        if (options.reload === false) return
        await router.reload({
          preserveScroll: options.preserveScroll ?? true,
          preserveState: options.preserveState ?? true,
        })
        hydrateKeelQuery(getPage())
        queryClient.invalidateQueries({ queryKey: ["keel", "page"], refetchType: "none" })
      },
    },
    queryClient,
  )
  return reactiveStore(mutation)
}

export function useKeelPageQuery() {
  const store = createQuery(
    {
      queryKey: ["keel", "page"] as const,
      queryFn: async () => {
        await router.reload({ preserveScroll: true, preserveState: true })
        return getPage()
      },
      initialData: getPage(),
      staleTime: Infinity,
    },
    getQueryClient(),
  )
  return reactiveStore(store)
}

/**
 * @internal Re-exported for tests only; not part of the package entry point.
 * See `reactiveStore.ts` for the subscription semantics (issue #15).
 */
export { reactiveStore } from "./reactiveStore.js"

export { ActionError }
