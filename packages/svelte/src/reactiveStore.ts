import { createSubscriber } from "svelte/reactivity"
import { get, type Readable } from "svelte/store"

/**
 * Wraps a Svelte readable in a reactive proxy for component code.
 *
 * State reads (any non-function value, e.g. `isPending`, `error`) subscribe
 * the current reactive scope through `track`. Method reads (`mutateAsync`,
 * `reset`, …) never subscribe: they return the function bound to the current
 * store value. This keeps an `$effect` that only calls a mutation method from
 * re-running when the mutation settles and firing the action forever
 * (issue #15).
 *
 * @internal Exported from this module for tests and re-exported by `query.ts`;
 * not part of the package entry point.
 * @param store Source readable store.
 * @param track Subscription seam for tests. Defaults to `createSubscriber`.
 */
export function reactiveStore<T extends object>(
  store: Readable<T>,
  track: () => void = createSubscriber((update) => store.subscribe(() => update())),
): T {
  return new Proxy({} as T, {
    get(_target, prop) {
      const current = get(store)
      const value = current[prop as keyof T]
      if (typeof value === "function") {
        return (value as (...args: never[]) => unknown).bind(current)
      }
      track()
      return value
    },
  })
}
