# Repository Guidelines

## Project Structure & Module Organization

CloudForge is a Java 25, Spring Boot 4.1, Gradle Groovy multi-project repository. Deployable applications live under `services/`; reusable Java modules live under `shared/`; architectural decisions live under `docs/adr/`. The skill catalog remains under `.agents/skills/`, with `.claude/skills/` symlinking to canonical skill directories. `skills-lock.json` records installed skill sources and hashes.

## Build, Test, and Development Commands

Use the checked-in Gradle Wrapper for application work:

- `make up` initializes and runs the complete local stack.
- `make init` prepares `.env`, the SDKMAN JDK, and Gradle.
- `make infra-up`, `make run`, and `make down` provide the split local lifecycle.
- `make format` applies repository formatting, Spring Java Format, and Java license headers.
- `make check` runs the complete merge gate: formatting, Checkstyle, Error Prone, NullAway, ArchUnit, and tests.
- `make test` runs tests without the static-analysis gates.
- `./gradlew test` runs all unit tests.
- `./gradlew build` compiles, tests, and packages every module.
- `./gradlew :services:gateway:test` tests the Gateway service independently.
- `./gradlew :services:iam:test` tests the IAM service independently.
- `./gradlew :services:gateway:bootRun` runs the local gateway.
- `./gradlew :services:iam:bootRun` runs IAM against the Compose dependencies.
- `docker compose config` validates the local infrastructure definition.
- `sdk env install && sdk env` installs and activates the Java version pinned in `.sdkmanrc`.

For skill or documentation changes, also use these lightweight checks:

- `python3 -m json.tool skills-lock.json >/dev/null` validates the lock file.
- `find .agents/skills -name SKILL.md -print` inventories skill entry points.
- `find .claude/skills -type l ! -exec test -e {} \; -print` reports broken skill symlinks.
- `find .agents/skills -name '*.sh' -exec bash -n {} +` syntax-checks shell helpers.
- `git diff --check` catches whitespace errors before review.

`.sdkmanrc` pins the developer JDK distribution and patch version. The wrapper provisions a compatible Java 25 toolchain when the SDKMAN-managed JDK is not installed locally.

## Coding Style & Naming Conventions

Java follows the executable Spring style defined in [`docs/development/code-style.md`](docs/development/code-style.md). Run `make format` before editing around formatter output and run `make check` before handing work to another developer or agent. The checked-in Gradle, Checkstyle, Spotless, Error Prone, NullAway/JSpecify, and ArchUnit configuration is authoritative. Do not weaken a rule or add a baseline to make a change pass. Every Java source file must retain the Apache 2.0 header generated from `config/license/java-header.txt`; production packages are `@NullMarked`, and genuine absence is explicitly `@Nullable`.

Write concise Markdown with ATX headings, short paragraphs, and relative links to adjacent references. Skill directories use kebab-case (for example, `.agents/skills/code-review/`). Keep YAML and JSON consistently two-space indented. Preserve valid YAML front matter at the top of every `SKILL.md`, including `name` and the appropriate invocation metadata. Put reusable automation in a skill-local `scripts/` directory; do not duplicate canonical skill content under `.claude/skills/`.

## Testing Guidelines

JUnit 5 is the default test framework. Name test classes with the `Tests` suffix, put them under each module's `src/test/java` tree, and test behavior through the module interface. ArchUnit tests are hard architecture gates with no frozen violation baseline. For documentation-only changes, run the structural checks above and verify every changed relative link and symlink. For shell changes, run `bash -n` plus a safe representative invocation.

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
