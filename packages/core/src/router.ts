import { announce, focusHost } from "./a11y.ts"
import { toContext } from "./context.ts"
import { syncCss } from "./css.ts"
import { emit, on } from "./events.ts"
import { pushSeed, seedFromHistory } from "./history.ts"
import { cacheSeed, prefetchSeed, readSeed, warmModule } from "./prefetch.ts"
import { beginVisit, endVisit, getPage, peekPage, setPage } from "./store.ts"
import { sendVisit } from "./transport.ts"
import {
  KEEL_HEADERS,
  type KeelSeed,
  type Method,
  type PageModule,
  type PendingVisit,
  type VisitOptions,
} from "./types.ts"

export interface RouterConfig {
  navigatePath?: string
  host?: Element | null
  focusOnNavigate?: boolean
  announce?: boolean | ((seed: KeelSeed) => string)
}

interface VisitResult {
  seed: KeelSeed
  partial: string[] | null
}

let config: RouterConfig = {}
let mounted: PageModule | null = null
let currentEntry: string | null = null
let inflight: AbortController | null = null

function hostEl(seed: KeelSeed): Element {
  if (config.host) return config.host
  const el = document.querySelector(seed.host || "#__keel_root")
  if (!el) throw new Error(`Keel: host node '${seed.host}' not found`)
  return el
}

function shouldPreserveScroll(options: VisitOptions, seed: KeelSeed): boolean {
  if (options.preserveScroll === "errors") return seed.hasOwnProperty("errors") && Object.keys(seed.errors).length > 0
  return Boolean(options.preserveScroll)
}

function buildUrl(href: string, method: Method, data: VisitOptions["data"]): string {
  if (method !== "get" || !data || data instanceof FormData) return href
  const url = new URL(href, typeof location === "undefined" ? "http://local.test" : location.origin)
  const params = data instanceof URLSearchParams ? data : new URLSearchParams()
  if (!(data instanceof URLSearchParams) && !(data instanceof FormData)) {
    for (const [key, value] of Object.entries(data)) {
      if (value == null) continue
      params.set(key, String(value))
    }
  }
  params.forEach((value, key) => url.searchParams.set(key, value))
  return url.pathname + url.search
}

export function visitRequestUrl(href: string, navigatePath?: string): string {
  if (navigatePath) return `${navigatePath}?to=${encodeURIComponent(href)}`
  return href
}

function mergePartial(seed: KeelSeed, partial: string[] | null): KeelSeed {
  if (!partial) return seed
  const previous = peekPage()?.data
  if (!previous || typeof previous !== "object" || Array.isArray(previous)) return seed
  if (!seed.data || typeof seed.data !== "object" || Array.isArray(seed.data)) return seed
  return { ...seed, data: { ...(previous as Record<string, unknown>), ...(seed.data as Record<string, unknown>) } }
}

function announcementFor(seed: KeelSeed): string | null {
  const option = config.announce
  if (option === false) return null
  if (typeof option === "function") return option(seed)
  return seed.head?.title ?? (typeof document !== "undefined" ? document.title : "")
}

async function fetchSeed(href: string, options: VisitOptions, signal: AbortSignal): Promise<VisitResult> {
  const method = options.method ?? "get"
  const url = visitRequestUrl(href, config.navigatePath)
  const headers = new Headers(options.headers)
  headers.set(KEEL_HEADERS.visit, "true")
  headers.set("Accept", "application/json")
  if (options.only?.length) headers.set(KEEL_HEADERS.only, options.only.join(","))
  if (options.except?.length) headers.set(KEEL_HEADERS.except, options.except.join(","))

  let body: BodyInit | undefined
  if (method !== "get" && options.data) {
    if (options.data instanceof FormData) {
      body = options.data
    } else {
      headers.set("Content-Type", "application/json")
      body = JSON.stringify(options.data)
    }
  }

  const response = await sendVisit(url, {
    method: method.toUpperCase(),
    headers,
    body,
    signal,
    onProgress: options.onProgress,
  })
  const partialHeader = response.headers.get(KEEL_HEADERS.partial)
  const partial = partialHeader != null ? partialHeader.split(",").map((part) => part.trim()).filter(Boolean) : null

  if (response.status === 422 || response.status === 404) {
    const seed = (await response.json()) as KeelSeed
    if (seed && typeof seed.page === "string") return { seed, partial }
  }
  if (!response.ok) {
    throw new Error(`Keel visit failed (${response.status}) for ${href}`)
  }
  return { seed: (await response.json()) as KeelSeed, partial }
}

async function applySeed(
  seed: KeelSeed,
  options: VisitOptions,
  replace: boolean,
  meta: { initial?: boolean } = {},
): Promise<void> {
  const sameEntry = currentEntry === seed.entry
  const samePage = peekPage()?.page === seed.page
  const preserve = Boolean(options.preserveState) && sameEntry && samePage && mounted?.update

  const run = async () => {
    const previousScroll = { x: window.scrollX, y: window.scrollY }
    if (preserve && mounted?.update) {
      setPage(seed)
      await mounted.update(toContext(seed, (href, opts) => visit(href, opts)))
    } else {
      if (mounted) await mounted.unmount()
      const mod = (await import(/* @vite-ignore */ seed.entry)) as PageModule
      mounted = mod
      currentEntry = seed.entry
      setPage(seed)
      await mod.mount(hostEl(seed), toContext(seed, (href, opts) => visit(href, opts)))
    }
    syncCss(seed.css ?? [])
    pushSeed(seed, replace)
    if (!shouldPreserveScroll(options, seed)) window.scrollTo(0, 0)
    else window.scrollTo(previousScroll.x, previousScroll.y)
    if (!meta.initial) {
      if (config.focusOnNavigate !== false) focusHost(hostEl(seed))
      const text = announcementFor(seed)
      if (text) announce(text)
    }
    emit("navigate", { page: seed })
  }

  if (options.viewTransition && typeof document !== "undefined" && "startViewTransition" in document) {
    await document.startViewTransition(() => run()).finished.catch(() => undefined)
    return
  }
  await run()
}

async function visit(href: string, options: VisitOptions = {}): Promise<void> {
  const method: Method = options.method ?? "get"
  const url = buildUrl(href, method, options.data)
  const visitState: PendingVisit = { url, method, cancelled: false }
  if (options.onBefore?.(visitState) === false) return
  emit("before", { visit: visitState })

  inflight?.abort()
  const controller = new AbortController()
  inflight = controller

  beginVisit()
  options.onStart?.(visitState)
  emit("start", { visit: visitState })

  try {
    const isPartial = Boolean(options.only?.length || options.except?.length)
    const cached = method === "get" && !isPartial ? readSeed(url) : null
    const fetched = cached ? { seed: cached, partial: null } : await fetchSeed(url, options, controller.signal)
    if (controller.signal.aborted) {
      visitState.cancelled = true
      options.onCancel?.()
      emit("cancel", { visit: visitState })
      return
    }
    const seed = mergePartial(fetched.seed, fetched.partial)
    if (seed.redirect) {
      await visit(seed.redirect, { ...options, method: "get", data: undefined })
      return
    }
    await applySeed(seed, options, Boolean(options.replace))
    if (Object.keys(seed.errors ?? {}).length > 0) {
      options.onError?.(seed.errors)
      emit("error", { errors: seed.errors })
    } else {
      options.onSuccess?.(seed)
      emit("success", { page: seed })
    }
  } catch (error) {
    if ((error as Error).name === "AbortError") {
      visitState.cancelled = true
      options.onCancel?.()
      emit("cancel", { visit: visitState })
    } else {
      throw error
    }
  } finally {
    options.onFinish?.(visitState)
    emit("finish", { visit: visitState })
    endVisit()
    if (inflight === controller) inflight = null
  }
}

async function prefetch(href: string, options: VisitOptions = {}): Promise<void> {
  emit("prefetching", { href })
  const url = buildUrl(href, "get", options.data)
  const isPartial = Boolean(options.only?.length || options.except?.length)
  if (isPartial) {
    const result = await fetchSeed(url, { ...options, method: "get" }, new AbortController().signal)
    await warmModule(result.seed.entry)
    emit("prefetched", { href, page: result.seed })
    return
  }
  const seed = await prefetchSeed(url, async () => {
    const result = await fetchSeed(url, { ...options, method: "get" }, new AbortController().signal)
    if (result.partial) return result.seed
    cacheSeed(url, result.seed)
    return result.seed
  })
  await warmModule(seed.entry)
  emit("prefetched", { href, page: seed })
}

function cancel(): void {
  inflight?.abort()
}

async function reload(options: VisitOptions = {}): Promise<void> {
  const page = getPage()
  await visit(page.path, { ...options, replace: true })
}

function listenHistory(): void {
  if (typeof window === "undefined" || (window as unknown as { __keelHistory?: boolean }).__keelHistory) return
  ;(window as unknown as { __keelHistory?: boolean }).__keelHistory = true
  window.addEventListener("popstate", (event) => {
    const seed = seedFromHistory(event)
    if (!seed) return
    void applySeed(seed, { preserveScroll: true, replace: true }, true)
  })
}

export const router = {
  visit,
  get: (href: string, data?: VisitOptions["data"], options?: VisitOptions) =>
    visit(href, { ...options, method: "get", data }),
  post: (href: string, data?: VisitOptions["data"], options?: VisitOptions) =>
    visit(href, { ...options, method: "post", data }),
  put: (href: string, data?: VisitOptions["data"], options?: VisitOptions) =>
    visit(href, { ...options, method: "put", data }),
  patch: (href: string, data?: VisitOptions["data"], options?: VisitOptions) =>
    visit(href, { ...options, method: "patch", data }),
  delete: (href: string, data?: VisitOptions["data"], options?: VisitOptions) =>
    visit(href, { ...options, method: "delete", data }),
  reload,
  replace: (href: string, options?: VisitOptions) => visit(href, { ...options, replace: true }),
  prefetch,
  cancel,
  on,
  configure(next: RouterConfig) {
    config = { ...config, ...next }
    listenHistory()
  },
}

export async function bootstrap(options: RouterConfig = {}): Promise<void> {
  router.configure(options)
  const node = document.getElementById("__keel_seed")
  if (!node?.textContent) throw new Error("Keel: missing #__keel_seed")
  const seed = JSON.parse(node.textContent) as KeelSeed
  await applySeed(seed, { replace: true }, true, { initial: true })
}
