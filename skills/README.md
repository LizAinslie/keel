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

```bash
npx skills add kolektivdev/keel
```

Installs all three skills into the current project. Useful flags:

- `-a claude-code -a grok -a kilo` — target specific agents (repeatable).
- `-g` — install globally (`~/.<agent>/skills`) instead of the project.
- `--copy` — copy instead of symlinking; useful on Windows or in Docker.
- `--skill keel-host` — install just one skill.

You can also copy the folders you want into the agent's project skills
directory (or symlink). One copy in this repo is the source of truth — do not
fork the markdown into per-agent trees.

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

`llms.txt` at the site root lists these skills with raw URLs.
