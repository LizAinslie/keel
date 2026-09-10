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
  let data = $state({ ...initial })
  let errors = $state<Record<string, string[]>>({})
  let processing = $state(false)
  let progress = $state<number | null>(null)
  let wasSuccessful = $state(false)
  let isDirty = $state(false)

  function markDirty() {
    isDirty = JSON.stringify(data) !== JSON.stringify(defaults)
  }

  const form: FormState<T> = {
    get data() {
      return data
    },
    set data(value) {
      data = value
      markDirty()
    },
    get errors() {
      return errors
    },
    set errors(value) {
      errors = value
    },
    get processing() {
      return processing
    },
    get progress() {
      return progress
    },
    get wasSuccessful() {
      return wasSuccessful
    },
    get isDirty() {
      return isDirty
    },
    set(keyOrValues: keyof T | Partial<T>, value?: unknown) {
      if (typeof keyOrValues === "object") {
        Object.assign(data, keyOrValues)
      } else {
        data[keyOrValues] = value as T[typeof keyOrValues]
      }
      markDirty()
    },
    reset(...fields: (keyof T)[]) {
      if (fields.length === 0) {
        data = { ...defaults }
      } else {
        for (const field of fields) data[field] = defaults[field]
      }
      isDirty = false
    },
    clearErrors() {
      errors = {}
    },
    async submit(method, href, options = {}) {
      processing = true
      wasSuccessful = false
      try {
        await router.visit(href, {
          ...options,
          method,
          data,
          onError(next) {
            errors = next
            options.onError?.(next)
          },
          onSuccess(page) {
            errors = {}
            wasSuccessful = true
            options.onSuccess?.(page)
          },
          onProgress(next) {
            progress = next.percentage
            options.onProgress?.(next)
          },
        })
      } finally {
        processing = false
      }
    },
    get: (href, options) => form.submit("get", href, options),
    post: (href, options) => form.submit("post", href, options),
    put: (href, options) => form.submit("put", href, options),
    patch: (href, options) => form.submit("patch", href, options),
    delete: (href, options) => form.submit("delete", href, options),
  }
  return form
}
