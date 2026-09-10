# Keel agent skills

Canonical Agent Skills for implementing Keel from either side of the wire.
Each folder is a valid skill (`SKILL.md` with YAML frontmatter) for Claude
Code, Grok, and any consumer of the [Agent Skills](https://agentskills.io)
layout.

| Skill | When |
| --- | --- |
| `keel-host` | Server / protocol / schema / CSRF / packs on the host |
| `keel-pack` | Svelte (or future React) pack authoring |
| `keel-scaffold` | `keel-scaffold origin dir` from `GET /__keel/schema` |

## Install

Copy the folders you want into the agent's project skills directory (or
symlink). One copy in this repo is the source of truth — do not fork the
markdown into per-agent trees.

```bash
# Claude Code (project)
cp -R skills/keel-host skills/keel-pack skills/keel-scaffold .claude/skills/

# Grok (project)
cp -R skills/keel-host skills/keel-pack skills/keel-scaffold .grok/skills/

# Agnostic / Codex / Copilot / Amp
mkdir -p .agents/skills
cp -R skills/keel-host skills/keel-pack skills/keel-scaffold .agents/skills/
```

Personal (all repos): the same paths under `~/.claude/skills`, `~/.grok/skills`,
or `~/.agents/skills`.

Marketplace-style CLIs that look for `skills/*/SKILL.md` at the repo root
(`npx skills add <owner>/<repo>`) can install from this repository as-is.

`llms.txt` at the site root lists these skills with raw URLs.
