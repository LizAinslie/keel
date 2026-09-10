import type { Component, Snippet } from "svelte"

declare const Head: Component<{
  title?: string
  description?: string
  canonical?: string
  image?: string
  type?: string
  children?: Snippet
}>
export default Head
