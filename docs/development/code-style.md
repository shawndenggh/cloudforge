# Code style and quality gate

CloudForge uses an executable quality gate. Human review may add stricter feedback, but it must not replace or contradict the checked-in configuration. Every developer and AI agent must run `make format` followed by `make check` before handing off a change.

## Build conventions

The root build applies `cloudforge.java-conventions` to every module, providing the default `java` plugin and shared quality rules. Each project below `shared/` explicitly applies `java-library`. The convention plugin in `build-logic/` owns the shared Java toolchain, dependency BOMs, formatting, static analysis, null safety, and architecture-test dependencies. Dependency coordinates and versions remain in `gradle/libs.versions.toml`; module builds declare only their framework or library plugin and application dependencies.

## Java style

Java follows the Spring team style through Spring Java Format and its Checkstyle rules:

- use tabs for Java indentation and spaces only for alignment;
- keep lines within 120 characters where practical;
- do not use wildcard imports;
- avoid static imports in production code; focused test assertions may use them;
- name JUnit test classes with the `Tests` suffix;
- arrange a class so its public API reads before implementation details;
- prefer explicit local types in production code; use `var` in tests only when it improves readability.

Run `make format` to apply the source formatter and required license header. IDE formatting is optional; the Gradle tasks are authoritative.

## License headers

The project is licensed under Apache License 2.0. Every Java source file carries the full header from `config/license/java-header.txt`:

```text
Copyright 2026-present Shawn Deng and CloudForge contributors.
```

Do not copy or hand-edit the header in individual files. Change the template and run `make format` when project ownership or licensing changes.

## Null safety

Production packages are non-null by default with JSpecify `@NullMarked`. Mark only genuine absence with `@Nullable`; do not use `Optional` for fields or method parameters merely to silence analysis. NullAway runs as an Error Prone error on production compilation. Test source is excluded from NullAway so tests can exercise invalid and nullable inputs directly.

## Architecture rules

ArchUnit tests are hard gates with no frozen baseline. Existing code and every new change must satisfy the current rules. The initial rules preserve these dependency directions:

- IAM domain packages do not depend on Spring, persistence, messaging infrastructure, or OAuth/OIDC protocol adapters;
- Gateway does not depend on IAM implementation classes;
- shared messaging contracts remain framework independent;
- the shared security library remains independent from Spring Boot and deployable services.

Add a rule in the owning module when introducing a durable boundary. Do not freeze a violation; fix the dependency direction.

## Suppressions and generated code

Suppress a diagnostic only at the narrowest declaration, naming the exact rule and documenting why the code is safe. Broad suppressions such as `all`, disabling Error Prone/NullAway for a package, or formatter-off regions are not accepted. Generated source may receive a scoped exclusion only when generation is introduced and the generated directory is unambiguous.

## Commands

```bash
make format  # Apply repository formatting, Java formatting, and Java headers
make check   # Formatting, Checkstyle, Error Prone, NullAway, ArchUnit, and tests
make test    # Run tests only
```

`make check` must pass locally and in GitHub Actions before merge.
