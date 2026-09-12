import assert from "node:assert/strict"
import { test } from "node:test"
import { writable } from "svelte/store"
import { reactiveStore } from "../dist/reactiveStore.js"

interface CounterState {
  count: number
  isPending: boolean
  error: string | null
  increment(): number
}

function makeState(count = 1): CounterState {
  return {
    count,
    isPending: false,
    error: null,
    increment() {
      return this.count + 1
    },
  }
}

function makeStore(count = 1) {
  return writable<CounterState>(makeState(count))
}

test("state reads subscribe the tracker once per read", () => {
  const store = makeStore()
  let tracked = 0
  const proxy = reactiveStore(store, () => {
    tracked += 1
  })

  assert.equal(proxy.isPending, false)
  assert.equal(tracked, 1)
  assert.equal(proxy.error, null)
  assert.equal(tracked, 2)

  store.set({ ...makeState(2), isPending: true })
  assert.equal(proxy.isPending, true)
  assert.equal(tracked, 3)
})

test("method reads do not subscribe and bind to the current store value", () => {
  const store = makeStore()
  let tracked = 0
  const proxy = reactiveStore(store, () => {
    tracked += 1
  })

  const increment = proxy.increment
  assert.equal(tracked, 0)
  assert.equal(increment(), 2)

  store.set(makeState(41))
  assert.equal(proxy.increment(), 42)
  assert.equal(tracked, 0)
})

test("an effect that only calls a method does not resubscribe", () => {
  const store = makeStore()
  let tracked = 0
  let runs = 0
  const proxy = reactiveStore(store, () => {
    tracked += 1
  })

  const stop = store.subscribe(() => {
    runs += 1
    void proxy.increment()
  })

  assert.equal(runs, 1)
  store.set(makeState(7))
  assert.equal(runs, 2)
  assert.equal(tracked, 0)
  stop()
})
