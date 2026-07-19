# Domain Docs

How engineering work should consume CloudForge's final product and technical documentation.

## Before exploring, read these

- **`CONTEXT.md`** for the ubiquitous language.
- The relevant **PRD under `docs/product/`** for user behavior, scope, and acceptance criteria.
- The relevant **technical design under `docs/architecture/`** for data ownership, interfaces, security, migration, and tests.

If a feature has no PRD or technical design, proceed from current code and Issues. Create the missing document only when the work needs a new product or technical decision.

## File structure

```text
/
├── CONTEXT.md
└── docs/
    ├── product/
    │   └── <feature>-prd.md
    ├── architecture/
    │   └── <feature>-technical-design.md
    └── templates/
        ├── prd-template.md
        └── technical-design-template.md
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Resolve documentation conflicts

The PRD is authoritative for product scope and externally observable behavior. The technical design is authoritative for implementation decisions. If code or a proposed change contradicts either document, surface the conflict explicitly and update the owning document after the decision is confirmed:

> _Contradicts FR-007 and the current technical design. Confirm whether the requirement changed before implementation._

CloudForge does not use standalone ADRs. Durable decisions belong in the relevant PRD or technical design so developers have one source of truth.
