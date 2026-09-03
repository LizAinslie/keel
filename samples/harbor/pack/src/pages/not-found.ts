import { createPage } from "../lib/mount"
import NotFound from "./not-found.svelte"

export const { mount, unmount } = createPage(NotFound)
