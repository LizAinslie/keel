import { getCollection, type CollectionEntry } from "astro:content"

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
