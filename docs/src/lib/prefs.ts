import {
  DEFAULT_CODE_THEME,
  DEFAULT_SITE_THEME,
  isCodeTheme,
  migrateSiteTheme,
  type CodeTheme,
  type SiteTheme,
} from "./themes"

export const PREF_KEYS = {
  theme: "keel:theme",
  codeTheme: "keel:code-theme",
  lang: "keel:lang",
  frontend: "keel:frontend",
  backend: "keel:backend",
} as const

export type LangName = "ts" | "js"
export type FrontendName = "svelte" | "react"
export type BackendName = "ktor" | "soon"

export type Prefs = {
  theme: SiteTheme
  codeTheme: CodeTheme
  lang: LangName
  frontend: FrontendName
  backend: BackendName
}

export const DEFAULTS: Prefs = {
  theme: DEFAULT_SITE_THEME,
  codeTheme: DEFAULT_CODE_THEME,
  lang: "ts",
  frontend: "svelte",
  backend: "ktor",
}

function pick<T extends string>(value: string | null, allowed: readonly T[], fallback: T): T {
  return allowed.includes(value as T) ? (value as T) : fallback
}

export function readPrefs(): Prefs {
  if (typeof localStorage === "undefined") return { ...DEFAULTS }
  const codeTheme = localStorage.getItem(PREF_KEYS.codeTheme)
  return {
    theme: migrateSiteTheme(localStorage.getItem(PREF_KEYS.theme)),
    codeTheme: isCodeTheme(codeTheme) ? codeTheme : DEFAULTS.codeTheme,
    lang: pick(localStorage.getItem(PREF_KEYS.lang), ["ts", "js"] as const, DEFAULTS.lang),
    frontend: pick(localStorage.getItem(PREF_KEYS.frontend), ["svelte", "react"] as const, DEFAULTS.frontend),
    backend: pick(localStorage.getItem(PREF_KEYS.backend), ["ktor", "soon"] as const, DEFAULTS.backend),
  }
}

export function applyPrefs(prefs: Partial<Prefs>): void {
  const next = { ...readPrefs(), ...prefs }
  const root = document.documentElement
  root.setAttribute("data-theme", next.theme)
  root.dataset.codeTheme = next.codeTheme
  root.dataset.lang = next.lang
  root.dataset.frontend = next.frontend
  root.dataset.backend = next.backend
  localStorage.setItem(PREF_KEYS.theme, next.theme)
  localStorage.setItem(PREF_KEYS.codeTheme, next.codeTheme)
  localStorage.setItem(PREF_KEYS.lang, next.lang)
  localStorage.setItem(PREF_KEYS.frontend, next.frontend)
  localStorage.setItem(PREF_KEYS.backend, next.backend)
  document.dispatchEvent(new CustomEvent("keel:prefs", { detail: next }))
}

export function syncControls(root: ParentNode = document): void {
  const prefs = readPrefs()
  root.querySelectorAll<HTMLInputElement>("[data-pref]").forEach((el) => {
    const key = el.dataset.pref as keyof Prefs | undefined
    if (!key) return
    el.checked = el.value === prefs[key]
  })
  root.querySelectorAll<HTMLElement>("[data-theme-current]").forEach((el) => {
    const current = SITE_LABEL[prefs.theme]
    el.textContent = current
  })
  root.querySelectorAll<HTMLElement>("[data-code-theme-current]").forEach((el) => {
    el.textContent = prefs.codeTheme === "follow" ? "Follow" : CODE_LABEL[prefs.codeTheme]
  })
  root.querySelectorAll<HTMLButtonElement>("[data-lang-btn]").forEach((el) => {
    const block = el.closest<HTMLElement>(".keel-code")
    const current = (block?.dataset.lang as LangName | undefined) || prefs.lang
    const active = el.dataset.langBtn === current
    el.classList.toggle("btn-active", active)
    el.setAttribute("aria-pressed", String(active))
  })
}

const SITE_LABEL: Record<SiteTheme, string> = {
  "catppuccin-latte": "Latte",
  "catppuccin-frappe": "Frappé",
  "catppuccin-macchiato": "Macchiato",
  "catppuccin-mocha": "Mocha",
}

const CODE_LABEL: Record<Exclude<CodeTheme, "follow">, string> = {
  latte: "Latte",
  frappe: "Frappé",
  macchiato: "Macchiato",
  mocha: "Mocha",
}

export function copyFromFigure(root: HTMLElement): string {
  const filePanels = [...root.querySelectorAll<HTMLElement>("[data-file-panel]")]
  const file =
    filePanels.find((panel) => {
      const group = panel.closest<HTMLElement>("[data-framework-panel]")
      if (group && getComputedStyle(group).display === "none") return false
      return !panel.classList.contains("hidden") && getComputedStyle(panel).display !== "none"
    }) ?? root
  const langPanels = [...file.querySelectorAll<HTMLElement>("[data-lang-panel]")]
  const visible = langPanels.find((panel) => getComputedStyle(panel).display !== "none")
  const pre = (visible ?? file).querySelector("pre")
  return (pre?.innerText ?? "").replace(/\n$/, "")
}


