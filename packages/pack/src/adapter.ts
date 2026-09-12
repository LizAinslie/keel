/**
 * Shared by svelteFiles() and later tanstackFiles() / reactRouterFiles().
 * Adapters map files to page ids, never URL params.
 */
export interface DiscoveredPage {
  id: string
  file: string
  layouts: string[]
  /** Compiled `+head.svelte` markup with `{{seed.path}}` placeholders. */
  head?: string
}

export interface RouterAdapter {
  name: string
  discover(pagesDir: string): DiscoveredPage[]
  entrySource(page: DiscoveredPage): string
}
