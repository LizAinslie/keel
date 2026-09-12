import assert from "node:assert/strict"
import { test } from "node:test"
import { compileHeadTemplate } from "./head-template.ts"

test("compiles {seed.path} interpolations to host placeholders", () => {
  assert.equal(
    compileHeadTemplate(`<title>{seed.data.user.displayName} — Harbor</title>`),
    "<title>{{data.user.displayName}} — Harbor</title>",
  )
})

test("optional chaining is normalized to a plain seed path", () => {
  assert.equal(
    compileHeadTemplate(`<meta name="description" content="Messages from {seed?.data.user.displayName}." />`),
    '<meta name="description" content="Messages from {{data.user.displayName}}." />',
  )
  assert.equal(
    compileHeadTemplate(`<title>{seed?.data?.user.displayName}</title>`),
    "<title>{{data.user.displayName}}</title>",
  )
})

test("unwraps a single <svelte:head> block", () => {
  const source = `<script lang="ts">let { seed } = $props()</script>
<svelte:head>
  <title>{seed.data.title} — Harbor</title>
  <meta property="og:type" content="website" />
</svelte:head>`
  assert.equal(
    compileHeadTemplate(source),
    `<title>{{data.title}} — Harbor</title>
  <meta property="og:type" content="website" />`,
  )
})

test("strips script, style, and comment nodes", () => {
  const source = `<script lang="ts">import type { KeelSeed } from "@kolektiv/keel"</script>
<style>.lede { color: red }</style>
<!-- ignore me -->
<title>Harbor</title>`
  assert.equal(compileHeadTemplate(source), "<title>Harbor</title>")
})

test("leaves already-compiled {{placeholders}} untouched", () => {
  assert.equal(
    compileHeadTemplate(`<meta property="og:title" content="{{data.user.displayName}}" />`),
    '<meta property="og:title" content="{{data.user.displayName}}" />',
  )
})

test("rejects block and html mustaches", () => {
  assert.throws(() => compileHeadTemplate("<title>{#if seed.data.x}Hi{/if}</title>"), /not blocks/)
  assert.throws(() => compileHeadTemplate("<title>{@html seed.data.x}</title>"), /not blocks/)
})

test("rejects interpolations that are not seed paths", () => {
  assert.throws(() => compileHeadTemplate("<title>{title}</title>"), /must be seed\.\* paths/)
  assert.throws(() => compileHeadTemplate(`<title>{data.title}</title>`), /must be seed\.\* paths/)
})

test("rejects an unterminated mustache", () => {
  assert.throws(() => compileHeadTemplate("<title>{seed.data.title</title>"), /unterminated/)
})

test("throws when the template produces no markup", () => {
  assert.throws(() => compileHeadTemplate("<script>let x = 1</script>"), /produced no head markup/)
  assert.throws(() => compileHeadTemplate("   \n  "), /produced no head markup/)
})
