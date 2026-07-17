# Executable Spring code quality gate

CloudForge adopts Spring Java Format and reduced Spring Checkstyle as its Java style, with Spotless enforcing the Apache 2.0 source header, Error Prone plus NullAway/JSpecify enforcing compile-time correctness, and ArchUnit protecting module boundaries. These checks apply to all current production code with no legacy or architecture baseline and are required by `make check` and CI; this favors consistency with the Spring ecosystem and deterministic AI contributions over Google Java Format compatibility or review-only conventions.
