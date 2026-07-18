# Gradle Groovy multi-project monorepo

CloudForge uses a Gradle multi-project monorepo with Groovy DSL and a pinned Gradle Wrapper. Independently deployable applications live under `services/`, reusable libraries live under `shared/`, and individual module names omit the redundant `cloudforge-` prefix. Shared convention plugins keep build behavior consistent; the main build and the independent `build-logic` included build own separate version catalogs for application and build-tool dependencies.
