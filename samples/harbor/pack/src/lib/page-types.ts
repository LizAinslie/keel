export interface PostSummary {
  slug: string
  title: string
  excerpt: string
  published: string
}

export interface HomePage {
  kicker: string
  title: string
  lede: string
  posts: PostSummary[]
}

export interface ListPage {
  title: string
  query: string
  posts: PostSummary[]
}

export interface PostPage {
  slug: string
  title: string
  body: string
  published: string
}

export interface AboutPage {
  title: string
  copy: string
  pack: string
}

export interface NotFoundPage {
  path: string
  title: string
}
