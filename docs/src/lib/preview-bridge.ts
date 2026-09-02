const CHANNEL = "grok-preview-bridge"
const VERSION = 1

function isSafePath(path: string): boolean {
  if (!path.startsWith("/") || path.startsWith("//") || path.includes("\\")) return false
  try {
    return new URL(path, "https://preview.invalid").origin === "https://preview.invalid"
  } catch {
    return false
  }
}

export function installPreviewBridge(paths: string[]): () => void {
  if (typeof window === "undefined" || window.parent === window) return () => undefined
  const parentOrigin = document.referrer ? new URL(document.referrer).origin : null
  if (!parentOrigin) return () => undefined

  const onMessage = (event: MessageEvent) => {
    const data = event.data as { channel?: string; type?: string; path?: string; delta?: number }
    if (!data || data.channel !== CHANNEL) return
    if (data.type === "navigate" && typeof data.path === "string" && isSafePath(data.path)) {
      window.location.assign(data.path)
    }
    if (data.type === "history" && (data.delta === 1 || data.delta === -1)) {
      window.history.go(data.delta)
    }
    if (data.type === "hello") {
      window.parent.postMessage(
        { channel: CHANNEL, version: VERSION, type: "ready", paths },
        parentOrigin,
      )
    }
  }
  window.addEventListener("message", onMessage)
  window.parent.postMessage({ channel: CHANNEL, version: VERSION, type: "hello", paths }, parentOrigin)
  return () => window.removeEventListener("message", onMessage)
}
