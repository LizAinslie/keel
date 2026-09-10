import type { PageModule } from "@kolektiv/keel"
import { mount, unmount, type Component } from "svelte"
import Wrap from "./Wrap.svelte"

export function createPage(Page: Component, layouts: Component[] = []): PageModule {
  let app: ReturnType<typeof mount> | undefined

  return {
    async mount(host: Element) {
      if (app) {
        unmount(app)
        app = undefined
      }
      host.replaceChildren()
      try {
        app = mount(Wrap, { target: host, props: { layouts, Page } })
      } catch (error) {
        const message = error instanceof Error ? error.stack ?? error.message : String(error)
        const pre = document.createElement("pre")
        pre.setAttribute("data-keel-mount-error", "")
        pre.textContent = message
        host.append(pre)
        throw error
      }
    },
    async unmount() {
      if (!app) return
      unmount(app)
      app = undefined
    },
    update() {},
  }
}
