#!/usr/bin/env node
import { parseArgs } from "node:util"
import { scaffoldPack } from "./scaffold.ts"

const { values, positionals } = parseArgs({
  allowPositionals: true,
  options: {
    schema: { type: "string" },
    framework: { type: "string" },
    id: { type: "string" },
    version: { type: "string" },
    force: { type: "boolean", default: false },
  },
})

function usage(): never {
  console.error(
    "usage: keel-scaffold <origin> <dir>\n       keel-scaffold --schema <file.json> <dir>\n\nFetches GET {origin}/__keel/schema and writes a blank Svelte pack.",
  )
  process.exit(1)
}

const framework = values.framework ?? "svelte"
if (framework !== "svelte" && framework !== "react") usage()

try {
  if (values.schema) {
    const outDir = positionals[0]
    if (!outDir) usage()
    const written = await scaffoldPack({
      outDir,
      schemaPath: values.schema,
      framework,
      id: values.id,
      version: values.version,
      force: values.force,
    })
    console.log(`wrote ${written.length} files in ${outDir}`)
  } else {
    const origin = positionals[0]
    const outDir = positionals[1]
    if (!origin || !outDir) usage()
    const written = await scaffoldPack({
      outDir,
      origin,
      framework,
      id: values.id,
      version: values.version,
      force: values.force,
    })
    console.log(`wrote ${written.length} files in ${outDir}`)
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : error)
  process.exit(1)
}
