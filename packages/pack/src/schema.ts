export const KEEL_SCHEMA_FORMAT = "keel/1" as const

export interface SchemaPage {
  type: string
  path?: string
  methods?: string[]
}

export interface SchemaAction {
  in: string
  out: string
}

export interface SchemaType {
  kind: "object" | "enum" | string
  fields?: Record<string, string>
  values?: string[]
}

export interface PackSchema {
  format: string
  pagesName?: string
  pages: Record<string, SchemaPage>
  actions?: Record<string, SchemaAction>
  types?: Record<string, SchemaType>
}

export function parsePackSchema(raw: unknown, source: string): PackSchema {
  if (!raw || typeof raw !== "object") {
    throw new Error(`schema is not an object: ${source}`)
  }
  const obj = raw as Record<string, unknown>
  if (obj.format !== KEEL_SCHEMA_FORMAT) {
    throw new Error(`schema format must be ${KEEL_SCHEMA_FORMAT}, got ${String(obj.format)} (${source})`)
  }
  if (!obj.pages || typeof obj.pages !== "object" || Array.isArray(obj.pages)) {
    throw new Error(`schema is missing pages (${source})`)
  }
  return obj as unknown as PackSchema
}

export function originFromHost(input: string): string {
  const trimmed = input.trim().replace(/\/+$/, "")
  if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
  if (/^(localhost|127\.|0\.0\.0\.0|\[::)/i.test(trimmed)) return `http://${trimmed}`
  return `https://${trimmed}`
}

export function schemaUrl(origin: string): string {
  return `${originFromHost(origin)}/__keel/schema`
}
