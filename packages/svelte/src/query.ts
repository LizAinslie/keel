import { action, ActionError, getPage, router, type KeelSeed } from "@kolektiv/keel"
import { createMutation, createQuery, QueryClient } from "@tanstack/svelte-query"
import { createSubscriber } from "svelte/reactivity"
import { get, type Readable } from "svelte/store"

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

export interface UseActionOptions {
  /** Visit the current URL after a successful action. Default true. */
  reload?: boolean
  preserveScroll?: boolean
  preserveState?: boolean
}

/**
 * Typed POST to `/__keel/action/{id}`. On success the current page is
 * re-visited so `page()` and the TanStack cache rehydrate from the host.
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

function reactiveStore<T extends object>(store: Readable<T>): T {
  const watch = createSubscriber((update) => store.subscribe(() => update()))
  return new Proxy({} as T, {
    get(_target, prop) {
      watch()
      const current = get(store)
      const value = current[prop as keyof T]
      return typeof value === "function" ? (value as (...args: never[]) => unknown).bind(current) : value
    },
  })
}

export { ActionError }
