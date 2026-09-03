import { mount, unmount, type Component } from "svelte"

export function createPage(component: Component) {
  let app: ReturnType<typeof mount> | undefined

  return {
    async mount(host: Element) {
      if (app) {
        unmount(app)
        app = undefined
      }
      host.replaceChildren()
      app = mount(component, { target: host })
    },
    async unmount() {
      if (!app) return
      unmount(app)
      app = undefined
    },
  }
}
