/** Site base. `/` locally, `/keel/` on GitHub Pages. */
export const base = import.meta.env.BASE_URL

const root = base.endsWith("/") ? base.slice(0, -1) : base

/** Prefix an absolute site path with the configured base. */
export function path(to: string): string {
  if (to.startsWith("#") || to.startsWith("http://") || to.startsWith("https://")) return to
  if (to === "/") return root || "/"
  const suffix = to.startsWith("/") ? to : `/${to}`
  return `${root}${suffix}`
}

export function isDocsPath(pathname: string): boolean {
  const prefix = root
  const rel = prefix && pathname.startsWith(prefix) ? pathname.slice(prefix.length) || "/" : pathname
  return rel === "/docs" || rel.startsWith("/docs/")
}
