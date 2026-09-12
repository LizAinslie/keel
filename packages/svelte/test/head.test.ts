import assert from "node:assert/strict"
import { test } from "node:test"
import { applyHead } from "../dist/head.js"

class FakeElement {
  tagName: string
  attrs = new Map<string, string>()
  text = ""
  children: FakeElement[] = []
  parent: FakeElement | null = null

  constructor(tag: string) {
    this.tagName = tag.toUpperCase()
  }

  setAttribute(name: string, value: string): void {
    this.attrs.set(name, value)
  }

  getAttribute(name: string): string | null {
    return this.attrs.get(name) ?? null
  }

  get textContent(): string {
    return this.text
  }

  set textContent(value: string) {
    this.text = value
  }

  get content() {
    return { childNodes: this.children }
  }

  appendChild(child: FakeElement): FakeElement {
    child.parent = this
    this.children.push(child)
    return child
  }

  remove(): void {
    if (!this.parent) return
    this.parent.children = this.parent.children.filter((child) => child !== this)
    this.parent = null
  }

  querySelectorAll(selector: string): FakeElement[] {
    const out: FakeElement[] = []
    const matches = (el: FakeElement) => selector === "[data-keel-head]" && el.attrs.has("data-keel-head")
    const walk = (el: FakeElement) => {
      for (const child of el.children) {
        if (matches(child)) out.push(child)
        walk(child)
      }
    }
    walk(this)
    return out
  }

  set innerHTML(html: string) {
    this.children = parseHead(html)
    for (const child of this.children) child.parent = this
  }

  get innerHTML(): string {
    return ""
  }
}

function parseHead(html: string): FakeElement[] {
  const out: FakeElement[] = []
  let i = 0
  while (i < html.length) {
    const open = html.indexOf("<", i)
    if (open < 0) break
    const close = html.indexOf(">", open)
    if (close < 0) break
    const raw = html.slice(open + 1, close)
    const space = raw.search(/\s/)
    const name = (space < 0 ? raw : raw.slice(0, space)).replace(/\/$/, "").toLowerCase()
    const attrs = space < 0 ? "" : raw.slice(space)
    const el = new FakeElement(name)
    const attrRe = /([a-zA-Z-]+)(?:="([^"]*)")?/g
    let match: RegExpExecArray | null
    while ((match = attrRe.exec(attrs)) !== null) {
      el.setAttribute(match[1] ?? "", match[2] ?? "")
    }
    if (name === "title" || name === "script") {
      const end = html.indexOf(`</${name}>`, close)
      el.text = end < 0 ? "" : html.slice(close + 1, end)
      i = end < 0 ? html.length : end + name.length + 3
    } else {
      i = close + 1
    }
    out.push(el)
  }
  return out
}

function makeDocument() {
  const head = new FakeElement("head")
  const doc = {
    head,
    createElement(tag: string) {
      return new FakeElement(tag)
    },
    querySelector(selector: string) {
      return selector === "title" ? head.children.find((el) => el.tagName === "TITLE") ?? null : null
    },
  }
  Object.defineProperty(doc, "title", {
    get() {
      return head.children.find((el) => el.tagName === "TITLE")?.text ?? ""
    },
    set(value: string) {
      const title = head.children.find((el) => el.tagName === "TITLE")
      if (title) title.text = value
      else head.appendChild(new FakeElement("title")).text = value
    },
  })
  return doc
}

function mount(doc: ReturnType<typeof makeDocument>) {
  ;(globalThis as { document?: unknown }).document = doc
  ;(globalThis as { Element?: unknown }).Element = FakeElement
}

function unmount() {
  delete (globalThis as { document?: unknown }).document
  delete (globalThis as { Element?: unknown }).Element
}

function titles(doc: ReturnType<typeof makeDocument>): FakeElement[] {
  return doc.head.children.filter((el) => el.tagName === "TITLE")
}

test("pack head reuses the server title and leaves one data-keel-head set", () => {
  const doc = makeDocument()
  mount(doc)
  try {
    doc.head.appendChild(new FakeElement("title")).text = "Harbor"
    doc.head.children[0]?.setAttribute("data-keel-head", "")
    const cleanup = applyHead({
      title: "Harbor",
      html: '<title data-keel-head="">Harbor</title>\n<meta data-keel-head="" name="description" content="A board.">',
    })
    assert.equal(titles(doc).length, 1)
    assert.equal(doc.title, "Harbor")
    assert.equal(doc.head.querySelectorAll("[data-keel-head]").length, 2)
    cleanup()
    assert.equal(titles(doc).length, 1)
    assert.equal(doc.head.querySelectorAll("[data-keel-head]").length, 1)
  } finally {
    unmount()
  }
})

test("client navigation replaces the head without duplicating title or tags", () => {
  const doc = makeDocument()
  mount(doc)
  try {
    doc.head.appendChild(new FakeElement("title")).text = "Harbor"
    const home = applyHead({
      title: "Harbor",
      html: '<title data-keel-head="">Harbor</title>\n<meta data-keel-head="" property="og:title" content="Harbor">',
    })
    home()
    const user = applyHead({
      title: "Ada — Harbor",
      html: '<title data-keel-head="">Ada — Harbor</title>\n<meta data-keel-head="" property="og:title" content="Ada — Harbor">',
    })
    assert.equal(titles(doc).length, 1)
    assert.equal(doc.title, "Ada — Harbor")
    const tagged = doc.head.querySelectorAll("[data-keel-head]")
    assert.equal(tagged.length, 2)
    assert.ok(tagged.some((el) => el.tagName === "TITLE" && el.text === "Ada — Harbor"))
    assert.ok(tagged.some((el) => el.getAttribute("content") === "Ada — Harbor"))
    user()
    assert.equal(doc.head.querySelectorAll("[data-keel-head]").length, 1)
  } finally {
    unmount()
  }
})
