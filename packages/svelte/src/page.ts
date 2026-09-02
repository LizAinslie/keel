import {
  getPage,
  getProcessing,
  subscribe,
  subscribeProcessing,
  type KeelSeed,
} from "@kolektiv/keel"

type Subscriber = (fn: () => void) => () => void

function reactive<T>(read: () => T, watch: Subscriber): T & object {
  // Lazy Svelte 5 createSubscriber — keeps this file importable in Node tests.
  let notify: (() => void) | undefined
  try {
    const svelte = (globalThis as { __svelte_createSubscriber?: Subscriber }).__svelte_createSubscriber
    if (svelte) {
      notify = () => svelte(() => watch(() => undefined))
    }
  } catch {
    /* ignore */
  }
  const view = {
    get value() {
      notify?.()
      return read()
    },
  }
  return new Proxy(view as unknown as T & object, {
    get(_target, prop) {
      notify?.()
      const current = read() as Record<string | symbol, unknown>
      const value = current[prop]
      return typeof value === "function" ? value.bind(current) : value
    },
  })
}

export function page<T = unknown>(): KeelSeed<T> & { processing: boolean } {
  return reactive(
    () => ({ ...getPage<T>(), processing: getProcessing() > 0 }),
    (update) => {
      const a = subscribe(() => update())
      const b = subscribeProcessing(() => update())
      return () => {
        a()
        b()
      }
    },
  ) as KeelSeed<T> & { processing: boolean }
}
