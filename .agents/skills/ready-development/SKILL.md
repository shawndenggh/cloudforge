---
name: ready-development
description: Turn a PRD or feature description into a codebase-grounded technical design and a sequence of small, reviewable implementation Issues.
---

# Ready Development

Take a feature from product intent to a development-ready backlog. The destination is an approved technical design plus ordered Issues; implementation begins later, one Issue at a time.

Use three anchors throughout the run:

- **Evidence**: derive current behavior, constraints, and likely touchpoints from the repository rather than memory or user guesses.
- **Design gates**: close one architecture chapter before moving to the next.
- **Tracer bullets**: slice delivery into the smallest end-to-end changes that can be tested, reviewed, and reverted independently.

## Entry gate

If the invocation contains no feature description, PRD path, or Issue, ask exactly one question and wait:

> 你想为哪个功能做开发前技术设计？请提供 PRD 文档路径、GitHub Issue，或一段包含目标与范围的功能描述。

Accept either a PRD or a detailed description. A detailed description is sufficient when it identifies the user outcome, included behavior, explicit boundary, and acceptance result. When those product decisions remain unclear, pause architecture work and use [`writing-prd`](../writing-prd/SKILL.md) to settle them. Architecture must not silently invent product behavior.

When no formal PRD exists, preserve the confirmed description as the design input and assign stable `REQ-*` and `AC-*` identifiers for traceability. Create a separate PRD only when the user asks for one.

Treat an approved PRD boundary as an entry constraint. When the request contradicts that boundary, reopens a product non-goal, or bundles several new user capabilities, present the conflicting evidence and recommend returning to [`writing-prd`](../writing-prd/SKILL.md) to revise or split the product scope. Ask whether to take that product step and wait; begin no architecture gate until the revised scope is confirmed.

**Complete when:** one feature has a stable product outcome, scope boundary, and observable acceptance result.

## Step 1: Build the evidence map

Read repository instructions first. Then inspect:

- the supplied PRD, Issue, and related product documents;
- the repository's technical-design template and delivery handbook;
- the domain glossary or context document;
- related architecture documents, modules, interfaces, migrations, tests, and configuration;
- issue-tracker conventions and existing Issues that may overlap.

Use the repository's graph or code-navigation tools before broad text search when available. Trace callers, data ownership, trust boundaries, and test seams through actual code. Ask the user for decisions; obtain discoverable facts through legwork.

Summarize the evidence map with exact files or symbols, current behavior, existing seams, constraints, and contradictions between product intent, documentation, and code.

**Complete when:** every proposed change area is tied to current repository evidence, and every contradiction that could change the design is visible.

## Step 2: Lock the implementation scope

State:

1. the one-sentence implementation goal;
2. requirements and acceptance criteria included;
3. explicit technical non-goals;
4. existing behavior and contracts that must remain compatible;
5. the likely owning domain and service, supported by evidence.

Maintain a decision ledger throughout the interview:

- **Confirmed**: approved technical decisions.
- **Open**: unresolved decisions that affect architecture, safety, or Issue slicing.
- **Out of scope**: adjacent improvements deferred from this delivery.
- **Evidence**: repository facts supporting or constraining decisions.

Test every proposed addition with: **Does the approved product outcome fail without it?** Place anything else out of scope. Ask the user to confirm the scope lock before designing internals.

**Complete when:** one coherent delivery boundary is confirmed and adjacent cleanup, modernization, and future capabilities are excluded.

## Step 3: Interview one decision at a time

Apply the questioning discipline in [`grilling`](../grilling/SKILL.md): ask exactly one highest-impact unresolved technical decision, recommend one answer with its trade-off and code evidence, then wait.

Walk the repository's technical-design template in its defined order. If no template exists, use these seven design gates:

| Gate | Pressure test | Completion criterion |
| --- | --- | --- |
| 1. Design overview | Which product requirements change the system? What remains untouched? Which compatibility rules bind the solution? | Every in-scope requirement maps to a technical responsibility and every technical non-goal is explicit. |
| 2. Architecture and responsibilities | Which domain owns the behavior and data? Is this an existing service change or a new service? Where are the module interfaces, callers, dependencies, and seams? | Every changed responsibility has one owner; system and module diagrams show calls, ownership, and stable seams. |
| 3. Core flows | What starts each flow? Where are transactions? What happens on concurrency, retries, partial failure, or cancellation? | Every critical flow has a success path, material failure paths, consistency rules, and a sequence diagram when interactions are non-trivial. |
| 4. Identity, authorization, and isolation | Where does trusted identity originate? How is access decided? Which trust and tenant boundaries are crossed? How does disablement take effect? | Every protected path has an identity source, access decision point, isolation rule, and denial behavior. |
| 5. Data model and migration | What are the canonical resources, ownership, invariants, state transitions, constraints, indexes, retention, and rollout needs? | Every data change has a stable resource shape, safe migration order, compatibility plan, and rollback or explicit irreversibility. |
| 6. Interfaces and integrations | Which user, internal, event, or third-party contracts change? What are their errors, compatibility rules, idempotency, and loading behavior? | Every changed contract is listed in detail and callers can adopt it without guessing. |
| 7. Tests and acceptance | Which public seams prove behavior? Which normal, failure, security, isolation, migration, and compatibility cases matter? | Every requirement and risk maps to an observable test case at an agreed seam. |

Before Gate 2, read and apply [`codebase-design`](../codebase-design/SKILL.md). Prefer deep modules with small interfaces, put seams where behavior genuinely varies, and use the interface as the caller and test surface.

Before Gate 7, read and apply [`tdd`](../tdd/SKILL.md). Agree test seams with the user, design behavior-first tests, and express each implementation step as a red-to-green vertical slice.

After each answer:

1. restate one confirmed decision;
2. update the ledger;
3. check it against prior decisions, product scope, and repository evidence;
4. apply the scope test to any newly implied work;
5. ask the next unresolved question in the same gate.

When a domain term changes, apply [`domain-modeling`](../domain-modeling/SKILL.md) and update the repository glossary immediately. Record confirmed technical decisions in the technical design, not in a separate decision document.

**Complete when:** all seven gates meet their completion criteria and the Open ledger contains no blocking decision.

## Step 4: Write the technical design progressively

Copy the repository template to its normal architecture-document location after the scope lock is confirmed. Fill a chapter only from confirmed decisions and current evidence; update it when that chapter's gate closes. Preserve requirement identifiers so product requirements, implementation locations, and tests remain traceable.

Use UML-style Mermaid diagrams where relationships, ownership, states, or cross-module sequences are materially clearer than prose. Keep simple local behavior in prose or tables.

The technical design must make these facts explicit where applicable:

- domain and service ownership;
- module interfaces, seams, adapters, callers, and dependencies;
- data ownership, resource shape, schema evolution, and migration order;
- full interface contracts and compatibility behavior;
- identity, authorization, isolation, and trust boundaries;
- consistency, failure handling, observability, and rollback;
- test seams, cases, and requirement coverage.

**Complete when:** a developer can implement every in-scope requirement without inventing architecture, contracts, data rules, or test behavior.

## Step 5: Run the development-readiness review

Audit the design with the user one finding or decision at a time:

1. **Scope**: every design element is necessary for the confirmed outcome.
2. **Evidence**: file and symbol references still match the current repository.
3. **Ownership**: each behavior and data object has one authoritative owner.
4. **Interfaces**: callers, invariants, errors, compatibility, and trust are explicit.
5. **Safety**: migration, concurrency, failure, isolation, and rollback are addressed where relevant.
6. **Traceability**: each requirement reaches an implementation location and test case.
7. **Testability**: each slice can go red then green through an agreed public seam.

Present unresolved blockers and scope drift before proposing work items. Ask the user to approve the technical design.

**Complete when:** the user approves the design, all blocking questions are closed, and every readiness check passes or is explicitly marked not applicable.

## Step 6: Slice and publish Issues

Only after Step 5 completes, read [`references/issue-slicing.md`](references/issue-slicing.md) in full. Produce an ordered Issue plan with dependencies and show it to the user before publishing. Create tracker Issues only after explicit approval of that plan.

Stop after verifying the created Issues and identifying the first unblocked Issue. Leave production code, implementation commits, pushes, and implementation PRs to later sessions, each scoped to one approved Issue.

**Complete when:** the tracker contains an ordered, dependency-aware set of independently reviewable Issues linked to the PRD and technical design, and no implementation work has started.
