const SCRIPT_OR_STYLE = /<(script|style)\b[^>]*>[\s\S]*?<\/\1>/gi
const SVELTE_HEAD = /<svelte:head>([\s\S]*?)<\/svelte:head>/i
const HTML_COMMENT = /<!--[\s\S]*?-->/g
const SEED_PATH = /^seed\??\.(.+)$/

export function compileHeadTemplate(source: string, from = "+head.svelte"): string {
  let markup = source.replace(SCRIPT_OR_STYLE, "")
  const wrapped = SVELTE_HEAD.exec(markup)
  if (wrapped) markup = wrapped[1] ?? ""
  markup = markup.replace(HTML_COMMENT, "")
  const compiled = replaceMustaches(markup, from).trim()
  if (!compiled) {
    throw new Error(`${from}: +head.svelte produced no head markup`)
  }
  return compiled
}

function replaceMustaches(markup: string, from: string): string {
  let out = ""
  let i = 0
  while (i < markup.length) {
    const ch = markup[i]
    if (ch === "{") {
      if (markup[i + 1] === "{") {
        const close = markup.indexOf("}}", i + 2)
        if (close < 0) throw new Error(`${from}: unterminated {{`)
        out += markup.slice(i, close + 2)
        i = close + 2
        continue
      }
      const end = findMustacheEnd(markup, i)
      const expr = markup.slice(i + 1, end).trim()
      if (/^[#/@]/.test(expr)) {
        throw new Error(`${from}: v1 head markup only allows {seed.*} interpolations, not blocks`)
      }
      out += `{{${normalizeSeedPath(expr, from)}}}`
      i = end + 1
      continue
    }
    out += ch
    i += 1
  }
  return out
}

function findMustacheEnd(source: string, open: number): number {
  let i = open + 1
  let quote: string | undefined
  while (i < source.length) {
    const ch = source[i]
    if (quote) {
      if (ch === "\\") {
        i += 2
        continue
      }
      if (ch === quote) quote = undefined
      i += 1
      continue
    }
    if (ch === "'" || ch === '"' || ch === "`") {
      quote = ch
      i += 1
      continue
    }
    if (ch === "}") return i
    i += 1
  }
  throw new Error("unterminated {expression}")
}

function normalizeSeedPath(expr: string, from: string): string {
  const stripped = expr.replace(/\s+/g, "")
  const match = SEED_PATH.exec(stripped)
  if (!match) {
    throw new Error(`${from}: head interpolations must be seed.* paths (got '${expr}')`)
  }
  const path = (match[1] ?? "").replace(/\?\./g, ".")
  if (!/^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*$/.test(path)) {
    throw new Error(`${from}: invalid seed path '${expr}'`)
  }
  return path
}
