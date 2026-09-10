export const CATEGORY_LABELS: Record<string, string> = {
  "getting-started": "Getting started",
  "core-concepts": "Core concepts",
  advanced: "Advanced cases",
  implementing: "Implementing Keel",
}

export type SearchDoc = {
  id: string
  title: string
  description: string
  category: string
  href: string
  headings: string[]
  text: string
}

export type SearchHit = SearchDoc & { score: number }

export function searchDocs(index: SearchDoc[], query: string, limit = 8): SearchHit[] {
  const terms = query
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
  if (terms.length === 0) return []

  const hits: SearchHit[] = []
  for (const doc of index) {
    const title = doc.title.toLowerCase()
    const description = doc.description.toLowerCase()
    const headings = doc.headings.join(" ").toLowerCase()
    const text = doc.text.toLowerCase()
    let score = 0
    let matched = 0
    for (const term of terms) {
      let termScore = 0
      if (title === term) termScore += 120
      else if (title.startsWith(term)) termScore += 80
      else if (title.includes(term)) termScore += 50
      if (description.includes(term)) termScore += 20
      if (headings.includes(term)) termScore += 16
      if (doc.id.toLowerCase().includes(term)) termScore += 12
      if (text.includes(term)) termScore += 4
      if (termScore > 0) {
        matched += 1
        score += termScore
      }
    }
    if (matched === terms.length) hits.push({ ...doc, score })
  }
  return hits.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title)).slice(0, limit)
}

export function stripMdx(body: string): string {
  return body
    .replace(/^import .+$/gm, " ")
    .replace(/^export .+$/gm, " ")
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/[*_~]+/g, "")
    .replace(/\s+/g, " ")
    .trim()
}

export function headingsFrom(body: string): string[] {
  return [...body.matchAll(/^#{2,3}\s+(.+)$/gm)].map((match) =>
    match[1].replace(/[`*_]/g, "").replace(/\{.*?\}/g, "").trim(),
  )
}
