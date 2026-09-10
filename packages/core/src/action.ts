import { KEEL_ACTION_PATH } from "./types.ts"

export class ActionError extends Error {
  errors: Record<string, string[]>

  constructor(errors: Record<string, string[]>) {
    super("Keel action validation failed")
    this.name = "ActionError"
    this.errors = errors
  }
}

export async function action<I, O>(id: string, input: I): Promise<O> {
  const response = await fetch(`${KEEL_ACTION_PATH}/${id}`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(input),
  })
  if (response.status === 422) {
    const payload = (await response.json()) as { errors?: Record<string, string[]> }
    throw new ActionError(payload.errors ?? {})
  }
  if (!response.ok) {
    throw new Error(`Keel action failed (${response.status}) for ${id}`)
  }
  const payload = (await response.json()) as { data: O }
  return payload.data
}
