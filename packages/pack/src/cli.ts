#!/usr/bin/env node
import { parseArgs } from "node:util"
import { packFeb } from "./pack-feb.ts"

const { values } = parseArgs({
  options: {
    dir: { type: "string" },
    out: { type: "string" },
    contract: { type: "string" },
  },
})

if (!values.dir || !values.out) {
  console.error("usage: keel-pack --dir <dist> --out <file.feb> [--contract <page-types.ts|page-ids.json>]")
  process.exit(1)
}

packFeb({ distDir: values.dir, outFile: values.out, contract: values.contract })
console.log(`wrote ${values.out}`)
