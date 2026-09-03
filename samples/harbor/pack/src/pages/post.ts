import { createPage } from "../lib/mount"
import Post from "./post.svelte"

export const { mount, unmount } = createPage(Post)
