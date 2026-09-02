export const SITE_THEMES = [
  { id: "catppuccin-latte", label: "Latte", scheme: "light", swatch: "#eff1f5" },
  { id: "catppuccin-frappe", label: "Frappé", scheme: "dark", swatch: "#303446" },
  { id: "catppuccin-macchiato", label: "Macchiato", scheme: "dark", swatch: "#24273a" },
  { id: "catppuccin-mocha", label: "Mocha", scheme: "dark", swatch: "#1e1e2e" },
] as const

export type SiteTheme = (typeof SITE_THEMES)[number]["id"]

export const CODE_THEMES = [
  { id: "follow", label: "Follow site theme" },
  { id: "latte", label: "Latte" },
  { id: "frappe", label: "Frappé" },
  { id: "macchiato", label: "Macchiato" },
  { id: "mocha", label: "Mocha" },
] as const

export type CodeTheme = (typeof CODE_THEMES)[number]["id"]

export const SHIKI_THEMES = {
  mocha: "catppuccin-mocha",
  macchiato: "catppuccin-macchiato",
  frappe: "catppuccin-frappe",
  latte: "catppuccin-latte",
} as const

export const DEFAULT_SITE_THEME: SiteTheme = "catppuccin-mocha"
export const DEFAULT_CODE_THEME: CodeTheme = "follow"

export function isSiteTheme(value: string | null): value is SiteTheme {
  return SITE_THEMES.some((theme) => theme.id === value)
}

export function isCodeTheme(value: string | null): value is CodeTheme {
  return CODE_THEMES.some((theme) => theme.id === value)
}

export function migrateSiteTheme(value: string | null): SiteTheme {
  if (value === "keel-light") return "catppuccin-latte"
  if (value === "keel-dark") return "catppuccin-mocha"
  return isSiteTheme(value) ? value : DEFAULT_SITE_THEME
}
