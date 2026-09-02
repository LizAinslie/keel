import { router, type Method, type VisitOptions } from "@kolektiv/keel"

export interface FormState<T extends Record<string, unknown>> {
  data: T
  errors: Record<string, string[]>
  processing: boolean
  progress: number | null
  wasSuccessful: boolean
  isDirty: boolean
  set<K extends keyof T>(key: K, value: T[K]): void
  set(values: Partial<T>): void
  reset(...fields: (keyof T)[]): void
  clearErrors(): void
  submit(method: Method, href: string, options?: VisitOptions): Promise<void>
  get(href: string, options?: VisitOptions): Promise<void>
  post(href: string, options?: VisitOptions): Promise<void>
  put(href: string, options?: VisitOptions): Promise<void>
  patch(href: string, options?: VisitOptions): Promise<void>
  delete(href: string, options?: VisitOptions): Promise<void>
}

export function useForm<T extends Record<string, unknown>>(initial: T): FormState<T> {
  const defaults = { ...initial }
  const state: FormState<T> = {
    data: { ...initial },
    errors: {},
    processing: false,
    progress: null,
    wasSuccessful: false,
    isDirty: false,
    set(keyOrValues: keyof T | Partial<T>, value?: unknown) {
      if (typeof keyOrValues === "object") {
        Object.assign(state.data, keyOrValues)
      } else {
        state.data[keyOrValues] = value as T[typeof keyOrValues]
      }
      state.isDirty = JSON.stringify(state.data) !== JSON.stringify(defaults)
    },
    reset(...fields: (keyof T)[]) {
      if (fields.length === 0) {
        state.data = { ...defaults }
      } else {
        for (const field of fields) state.data[field] = defaults[field]
      }
      state.isDirty = false
    },
    clearErrors() {
      state.errors = {}
    },
    async submit(method, href, options = {}) {
      state.processing = true
      state.wasSuccessful = false
      try {
        await router.visit(href, {
          ...options,
          method,
          data: state.data,
          onError(errors) {
            state.errors = errors
            options.onError?.(errors)
          },
          onSuccess(page) {
            state.errors = {}
            state.wasSuccessful = true
            options.onSuccess?.(page)
          },
          onProgress(progress) {
            state.progress = progress.percentage
            options.onProgress?.(progress)
          },
        })
      } finally {
        state.processing = false
      }
    },
    get: (href, options) => state.submit("get", href, options),
    post: (href, options) => state.submit("post", href, options),
    put: (href, options) => state.submit("put", href, options),
    patch: (href, options) => state.submit("patch", href, options),
    delete: (href, options) => state.submit("delete", href, options),
  }
  return state
}
