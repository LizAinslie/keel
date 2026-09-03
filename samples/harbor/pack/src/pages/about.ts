import { createPage } from "../lib/mount"
import About from "./about.svelte"

export const { mount, unmount } = createPage(About)
