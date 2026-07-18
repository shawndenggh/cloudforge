# Repository Guidelines

## Project Structure & Module Organization

CloudForge is early-stage and does not yet contain the planned Java/Spring application. `README.md` records the intended platform scope. Current content is the skill catalog under `.agents/skills/`; each kebab-case directory contains a required `SKILL.md` and may include references, `scripts/`, or `agents/openai.yaml`. `.claude/skills/` contains symlinks to those canonical directories. `skills-lock.json` records installed sources and hashes; update it alongside generated skill changes.

## Build, Test, and Development Commands

There is no application build or package-manager configuration yet. Use lightweight repository checks:

- `python3 -m json.tool skills-lock.json >/dev/null` validates the lock file.
- `find .agents/skills -name SKILL.md -print` inventories skill entry points.
- `find .claude/skills -type l ! -exec test -e {} \; -print` reports broken skill symlinks.
- `find .agents/skills -name '*.sh' -exec bash -n {} +` syntax-checks shell helpers.
- `git diff --check` catches whitespace errors before review.

When application modules are introduced, add their wrapper-based build and run commands here and in `README.md`.

## Coding Style & Naming Conventions

Write concise Markdown with ATX headings, short paragraphs, and relative links to adjacent references. Skill directories use kebab-case (for example, `.agents/skills/code-review/`). Keep YAML and JSON consistently two-space indented. Preserve valid YAML front matter at the top of every `SKILL.md`, including `name` and the appropriate invocation metadata. Put reusable automation in a skill-local `scripts/` directory; do not duplicate canonical skill content under `.claude/skills/`.

## Testing Guidelines

No automated framework or coverage threshold is configured. For documentation-only changes, run the structural checks above and verify every changed relative link and symlink. For shell changes, run `bash -n` plus a safe representative invocation. Future production code should include behavioral tests in the same change and document the command needed to run them.

## Commit & Pull Request Guidelines

History currently uses Conventional Commit-style subjects such as `docs: initialize CloudForge repository` and `chore: add skill`. Continue with an imperative, scoped summary (`docs: clarify skill installation`). Keep commits focused. Pull requests should explain the purpose, list validation performed, link relevant issues, and call out architectural or operational trade-offs. Preserve backward compatibility where practical; include screenshots only for visible UI changes.

## Security & Configuration

Do not commit credentials, tokens, local environment files, or machine-specific absolute paths. Examples must use placeholders, and new runtime configuration should document safe defaults and required environment variables.

## Agent skills

### Issue tracker

Issues and PRDs are tracked in GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

The default five-role triage label vocabulary is used. See `docs/agents/triage-labels.md`.

### Domain docs

This repository uses a single-context domain documentation layout. See `docs/agents/domain.md`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
