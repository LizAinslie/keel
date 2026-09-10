import { getCollection, type CollectionEntry } from "astro:content"
import { path } from "./paths"
import { headingsFrom, stripMdx, type SearchDoc } from "./search"

export const CATEGORIES = [
  { id: "getting-started", label: "Getting started" },
  { id: "core-concepts", label: "Core concepts" },
  { id: "advanced", label: "Advanced cases" },
  { id: "implementing", label: "Implementing Keel" },
] as const

export type CategoryId = (typeof CATEGORIES)[number]["id"]

export async function allDocs(): Promise<CollectionEntry<"docs">[]> {
  const entries = await getCollection("docs")
  return entries.sort((a, b) => {
    const cat = CATEGORIES.findIndex((c) => c.id === a.data.category) - CATEGORIES.findIndex((c) => c.id === b.data.category)
    if (cat !== 0) return cat
    return a.data.order - b.data.order
  })
}

export function groupedDocs(entries: CollectionEntry<"docs">[]) {
  return CATEGORIES.map((category) => ({
    ...category,
    pages: entries.filter((entry) => entry.data.category === category.id),
  }))
}

export async function searchIndex(): Promise<SearchDoc[]> {
  const entries = await allDocs()
  return entries.map((entry) => {
    const body = typeof entry.body === "string" ? entry.body : ""
    return {
      id: entry.id,
      title: entry.data.title,
      description: entry.data.description,
      category: entry.data.category,
      href: path(`/docs/${entry.id}`),
      headings: headingsFrom(body),
      text: stripMdx(body),
    }
  })
}
