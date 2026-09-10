const LIVE_ID = "__keel_live"

export function focusHost(el: Element): void {
  if (!(el instanceof HTMLElement)) return
  if (!el.hasAttribute("tabindex")) el.setAttribute("tabindex", "-1")
  el.focus({ preventScroll: true })
}

export function announce(text: string): void {
  if (typeof document === "undefined" || !text) return
  let region = document.getElementById(LIVE_ID)
  if (!region) {
    region = document.createElement("div")
    region.id = LIVE_ID
    region.setAttribute("aria-live", "polite")
    region.setAttribute("aria-atomic", "true")
    region.style.cssText =
      "position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0"
    document.body.appendChild(region)
  }
  region.textContent = ""
  const next = text
  requestAnimationFrame(() => {
    const node = document.getElementById(LIVE_ID)
    if (node) node.textContent = next
  })
}
