# Repository Guidelines

## Project Structure & Module Organization

CloudForge is a Java 25, Spring Boot 4.1, Gradle Groovy multi-project repository. Deployable applications live under `services/`; reusable Java modules live under `shared/`; architectural decisions live under `docs/adr/`. The skill catalog remains under `.agents/skills/`, with `.claude/skills/` symlinking to canonical skill directories. `skills-lock.json` records installed skill sources and hashes.

## Build, Test, and Development Commands

Use the checked-in Gradle Wrapper for application work:

- `./gradlew test` runs all unit tests.
- `./gradlew build` compiles, tests, and packages every module.
- `./gradlew :services:gateway:test` tests the Gateway service independently.
- `./gradlew :services:iam:test` tests the IAM service independently.
- `./gradlew :services:gateway:bootRun` runs the local gateway.
- `./gradlew :services:iam:bootRun` runs IAM against the Compose dependencies.
- `docker compose config` validates the local infrastructure definition.

For skill or documentation changes, also use these lightweight checks:

- `python3 -m json.tool skills-lock.json >/dev/null` validates the lock file.
- `find .agents/skills -name SKILL.md -print` inventories skill entry points.
- `find .claude/skills -type l ! -exec test -e {} \; -print` reports broken skill symlinks.
- `find .agents/skills -name '*.sh' -exec bash -n {} +` syntax-checks shell helpers.
- `git diff --check` catches whitespace errors before review.

The wrapper provisions the Java 25 toolchain when it is not installed locally.

## Coding Style & Naming Conventions

Write concise Markdown with ATX headings, short paragraphs, and relative links to adjacent references. Skill directories use kebab-case (for example, `.agents/skills/code-review/`). Keep YAML and JSON consistently two-space indented. Preserve valid YAML front matter at the top of every `SKILL.md`, including `name` and the appropriate invocation metadata. Put reusable automation in a skill-local `scripts/` directory; do not duplicate canonical skill content under `.claude/skills/`.

## Testing Guidelines

JUnit 5 is the default test framework. Put tests under each module's `src/test/java` tree and test behavior through the module interface. For documentation-only changes, run the structural checks above and verify every changed relative link and symlink. For shell changes, run `bash -n` plus a safe representative invocation.

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
