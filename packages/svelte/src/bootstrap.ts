import { bootstrap as start, subscribe, type RouterConfig } from "@kolektiv/keel"
import { getQueryClient, hydrateKeelQuery } from "./query.ts"

export async function bootstrap(options: RouterConfig = {}): Promise<void> {
  getQueryClient()
  const node = document.getElementById("__keel_seed")
  if (!node?.textContent) throw new Error("Keel: missing #__keel_seed")
  const seed = JSON.parse(node.textContent)
  hydrateKeelQuery(seed)
  subscribe((next) => {
    hydrateKeelQuery(next)
  })
  await start(options)
}
