import {
  getPage,
  getProcessing,
  subscribe,
  subscribeProcessing,
  type KeelSeed,
} from "@kolektiv/keel"
import { createSubscriber } from "svelte/reactivity"

export function page<T = unknown>(): KeelSeed<T> & { processing: boolean } {
  const watch = createSubscriber((update) => {
    const a = subscribe(() => update())
    const b = subscribeProcessing(() => update())
    return () => {
      a()
      b()
    }
  })
  return new Proxy({} as KeelSeed<T> & { processing: boolean }, {
    get(_target, prop) {
      watch()
      if (prop === "processing") return getProcessing() > 0
      const current = getPage<T>() as unknown as Record<string | symbol, unknown>
      const value = current[prop]
      return typeof value === "function" ? value.bind(current) : value
    },
  })
}
