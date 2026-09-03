import { createPage } from "../lib/mount"
import List from "./list.svelte"

export const { mount, unmount } = createPage(List)
