# Mandatory code style and quality standards

This document is mandatory for every human and AI contributor to CloudForge. It applies to production code, tests, build logic, configuration, and documentation. A change is not ready for review, handoff, or merge until it satisfies this document and the executable quality gate.

The words **must**, **must not**, **should**, and **should not** are normative. Checked-in configuration is the source of truth. If this document and an executable rule disagree, contributors must follow the executable rule and correct the documentation in the same change.

## Required workflow

Every contributor must run the following commands from the repository root before handing off a change:

```bash
make format
make check
```

`make format` applies repository formatting, Spring Java Format, and Java license headers. `make check` is the complete merge gate: formatting, Checkstyle, Error Prone, NullAway/JSpecify, ArchUnit, and tests. A targeted module test is useful during development but does not replace the complete merge gate.

Contributors must not commit formatter output separately from the change that required it, and must not hand-edit generated formatter output to fight the configured tools.

## Repository and build conventions

- The root build must apply `cloudforge.java-conventions` to every Java subproject. Individual modules must not repeat that plugin declaration.
- Every project below `shared/` must explicitly apply `java-library` because shared modules publish library APIs rather than deployable applications.
- External application and library coordinates must use aliases from `gradle/libs.versions.toml`. Module build files must not contain hard-coded external dependency versions.
- Build-tool coordinates must remain in `build-logic/gradle/libs.versions.toml`. Spring Java Format is the only Spring-owned build dependency permitted in `build-logic`.
- A module must declare its own framework platform when it needs one. Deployable services own their Spring Boot or Spring Cloud platforms; shared libraries must not inherit those platforms from the root build.
- Build files must contain only the plugins, platforms, project dependencies, and external dependencies required by that module.

## Dependency boundaries

- Modules below `services/` may use Spring Boot starters and application infrastructure.
- Modules below `shared/` must not apply the Spring Boot plugin or depend on `spring-boot-starter-*` artifacts.
- A shared module may use a directly required lower-level Spring Framework or Spring Security library, but it must remain independently usable and must not depend on a deployable service.
- Shared APIs must not expose service implementation types.
- Code must declare the dependencies it uses directly and must not rely on an unrelated dependency to provide a transitive library accidentally.
- Dependency versions must be updated centrally through the appropriate version catalog and verified with `make check`.

## Java formatting and naming

Spring Java Format is authoritative for Java layout. Contributors must follow these additional rules:

- use tabs for Java indentation and spaces only for alignment;
- keep lines within 120 characters unless the formatter produces a longer line;
- do not use wildcard imports;
- avoid static imports in production code; focused test assertions may use them;
- use lower-case package names and standard Java type, method, field, and constant naming;
- name JUnit test classes with the `Tests` suffix;
- arrange a class so its public API reads before implementation details;
- prefer explicit local types in production code; use `var` in tests only when it improves readability.

IDE formatting is optional. The Gradle formatter and checks are authoritative.

## Non-Java formatting

All text files must use UTF-8, LF line endings, a final newline, and no trailing whitespace. `.editorconfig` defines the editor-facing rules, while Spotless enforces repository-level whitespace rules.

| Files | Indentation |
| --- | --- |
| Java | Tabs, width 4 |
| Gradle and Groovy | 4 spaces |
| JSON, Markdown, TOML, YAML | 2 spaces |
| XML and properties | 4 spaces |
| Makefile recipes | Tabs, width 4 |

Contributors must preserve valid syntax and established key ordering when editing structured configuration. Formatting must not be used to rewrite unrelated files.

## License headers

Every Java source file must carry the Apache 2.0 header generated from `config/license/java-header.txt`, including:

```text
Copyright 2026-present Shawn Deng and CloudForge contributors.
```

Contributors must not copy or hand-edit the header in individual files. Change the template and run `make format` when project ownership or licensing changes.

## Null safety

- Every production package must be non-null by default with JSpecify `@NullMarked`.
- Only genuine absence may be marked with `@Nullable`.
- Code must not use `Optional` for fields or method parameters merely to silence null analysis.
- Production compilation must pass NullAway in JSpecify mode without package-wide exclusions.
- Tests may exercise invalid and nullable inputs; test source remains outside NullAway enforcement.

## Tests and architecture

- JUnit 5 is the default test framework.
- Tests must be placed under the owning module and should verify behavior through the module interface.
- New behavior must include proportionate automated coverage.
- ArchUnit rules are hard gates with no frozen violation baseline.
- IAM domain packages must not depend on Spring, persistence, messaging infrastructure, or OAuth/OIDC protocol adapters.
- Gateway must not depend on IAM implementation classes.
- Shared messaging contracts must remain framework independent.
- Shared security must remain independent from Spring Boot and deployable services.
- A durable new dependency boundary must be protected by an ArchUnit rule in the owning module.

## Suppressions and prohibited bypasses

A diagnostic may be suppressed only on the narrowest declaration, with the exact rule name and a comment explaining why the code is safe.

Contributors must not:

- disable or weaken Checkstyle, Error Prone, NullAway, Spotless, Spring Java Format, or ArchUnit to make a change pass;
- add a frozen architecture baseline or broad warning suppression;
- use formatter-off regions for ordinary source code;
- exclude handwritten source from the quality gate;
- merge or hand off a change while `make check` fails.

Generated source may receive a scoped exclusion only when generation is introduced, the generated directory is unambiguous, and handwritten source remains fully checked.

## Command reference

```bash
make format  # Apply repository formatting, Java formatting, and Java headers
make check   # Run the complete mandatory quality gate
make test    # Run tests without replacing the complete quality gate
```

`make check` must pass locally and in GitHub Actions before merge.
