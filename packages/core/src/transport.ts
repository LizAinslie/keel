import { emit } from "./events.ts"

export interface ProgressPayload {
  percentage: number | null
}

export interface SendVisitOptions {
  method: string
  headers: Headers
  body?: BodyInit
  signal?: AbortSignal
  onProgress?: (progress: ProgressPayload) => void
}

/**
 * `fetch` for ordinary visits. XMLHttpRequest only when the body is FormData
 * and a progress callback is present — `fetch` cannot report upload progress.
 */
export async function sendVisit(url: string, options: SendVisitOptions): Promise<Response> {
  const method = options.method.toUpperCase()
  const useXhr = options.body instanceof FormData && typeof options.onProgress === "function"
  if (useXhr) return sendXhr(url, method, options)
  return fetch(url, {
    method,
    headers: options.headers,
    body: options.body,
    signal: options.signal,
  })
}

function sendXhr(url: string, method: string, options: SendVisitOptions): Promise<Response> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(method, url)
    options.headers.forEach((value, key) => {
      xhr.setRequestHeader(key, value)
    })
    const abort = () => xhr.abort()
    if (options.signal) {
      if (options.signal.aborted) {
        abort()
        reject(new DOMException("Aborted", "AbortError"))
        return
      }
      options.signal.addEventListener("abort", abort, { once: true })
    }
    xhr.upload.onprogress = (event) => {
      const percentage = event.lengthComputable ? Math.round((event.loaded / event.total) * 100) : null
      const progress = { percentage }
      options.onProgress?.(progress)
      emit("progress", progress)
    }
    xhr.onload = () => {
      options.signal?.removeEventListener("abort", abort)
      resolve(
        new Response(xhr.responseText, {
          status: xhr.status,
          statusText: xhr.statusText,
          headers: parseXhrHeaders(xhr.getAllResponseHeaders()),
        }),
      )
    }
    xhr.onerror = () => {
      options.signal?.removeEventListener("abort", abort)
      reject(new TypeError("Network request failed"))
    }
    xhr.onabort = () => {
      options.signal?.removeEventListener("abort", abort)
      reject(new DOMException("Aborted", "AbortError"))
    }
    xhr.send(options.body instanceof FormData ? options.body : null)
  })
}

function parseXhrHeaders(raw: string): Headers {
  const headers = new Headers()
  for (const line of raw.split(/[\r\n]+/)) {
    const index = line.indexOf(":")
    if (index === -1) continue
    headers.append(line.slice(0, index).trim(), line.slice(index + 1).trim())
  }
  return headers
}
