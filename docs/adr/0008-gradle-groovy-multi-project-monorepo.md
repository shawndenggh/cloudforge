# Gradle Groovy multi-project monorepo

CloudForge uses a Gradle multi-project monorepo with Groovy DSL and a pinned Gradle Wrapper. Independently deployable applications live under `services/`, reusable libraries live under `shared/`, and individual module names omit the redundant `cloudforge-` prefix; shared convention plugins and a version catalog keep build behavior and dependency versions consistent.
