import { createPage } from "../lib/mount"
import Home from "./home.svelte"

export const { mount, unmount } = createPage(Home)
