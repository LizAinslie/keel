import type { KeelSeed, PageContext } from "./types.ts"

export function toContext<T>(
  seed: KeelSeed<T>,
  navigate: PageContext<T>["navigate"],
): PageContext<T> {
  return {
    page: seed.page,
    path: seed.path,
    params: seed.params,
    data: seed.data,
    errors: seed.errors,
    theme: seed.theme,
    shared: seed.shared ?? {},
    navigate,
  }
}
