---
name: writing-prd
description: Write and revise focused product requirement documents through a chapter-by-chapter product interview. Use when the user wants to clarify a product idea, draft or improve a PRD, define MVP scope, user stories, product flows, business rules, or acceptance criteria.
---

# Writing PRDs

Use two anchors throughout the run:

- **Product lens**: express the user's problem, product behavior, and observable outcome in language a product manager can own.
- **Scope lock**: keep every question and requirement necessary to the current outcome; park adjacent ideas outside the current delivery.

## Working rules

### Speak product language

Translate implementation input into product meaning before discussing or writing it:

| Implementation-shaped input | Product-language expression |
| --- | --- |
| Cache or server-side session | Keep the user's login state current |
| Unique database constraint | The email must be unique |
| Forbidden response code | A user without access is refused without seeing protected information |
| Database initialization command | The first platform administrator exists after system initialization |

Use agreed domain terms when they help product and engineering share meaning, and define them on first use. Keep interface paths, request parameters, status codes, schemas, field types, storage, messaging, transactions, frameworks, deployment, and developer commands in the technical design rather than the PRD.

### Hold the scope lock

Maintain a short decision ledger while interviewing:

- **Confirmed**: decisions the user has approved.
- **Open**: decisions that still change product behavior or scope.
- **Out of scope**: adjacent capabilities explicitly deferred.

Test every new idea with one question: **Does the current user outcome fail without it?** If not, move it to non-goals or a later follow-up. Do not invent adjacent capabilities merely because they are common, technically convenient, or useful in a future version.

### Interview one decision at a time

- Ask the highest-impact unresolved product question in the current chapter.
- Give a recommended answer and its product trade-off before asking for the decision.
- Wait for the user's answer before moving on.
- Look up facts from the repository or available tools; ask the user only for product decisions.
- Restate each answer as one confirmed decision and never ask it again unless a contradiction appears.
- When the user wants to move faster, decide low-risk details with clearly stated assumptions and ask only blocking questions. Speed must not expand scope.

## Step 1: Establish product context

Read the repository's PRD template, glossary, related product documents, and existing requirements before interviewing. For an existing PRD, preserve confirmed decisions and identify only gaps, conflicts, technical leakage, and scope drift.

Do not make the user repeat facts already available in the workspace. Keep implementation facts in the background and surface only their product consequence.

**Complete when:** you can state the current problem, target user, desired outcome, known constraints, and the applicable PRD structure in product language.

## Step 2: Set the scope lock

State a one-sentence product problem and a one-sentence MVP boundary. Ask the user to confirm them before exploring details. Record newly suggested adjacent capabilities under **Out of scope** unless the current outcome depends on them.

**Complete when:** target users, desired outcome, included capabilities, and explicit non-goals describe one coherent delivery.

## Step 3: Interview chapter by chapter

Walk the chapters in this order. The prompts are a question bank, not a batch: ask only one question at a time and remain in the chapter until its completion gate is met.

| Chapter | Pressure-test | Completion gate |
| --- | --- | --- |
| 1. Background and goals | Who has the problem? What happens today? What outcome should change? | One concrete problem, affected users, and a verifiable outcome are clear. |
| 2. Scope and non-goals | What must MVP deliver? What tempting adjacent capabilities are deferred? | Included and excluded behavior form a stable boundary. |
| 3. Domain language | Which terms are overloaded? Which distinctions must product and engineering share? | Every core concept has one name and no unresolved synonym conflict. |
| 4. Users and access | Which user types exist? What can each see and do? | Every actor has an explicit goal, visible range, and allowed product actions. |
| 5. User stories | What does each actor need and why? What abnormal or lifecycle story matters? | Every P0 capability is anchored to a user, need, and value. |
| 6. Core flows | What starts the flow? What does the user do? What result or failure do they see? | Every P0 journey has a start, happy path, completion, and important failure path. |
| 7. Functional requirements | What observable behavior must the product provide? What is the priority? | Requirements are numbered, testable, product-shaped, and within scope. |
| 8. Business rules | What must always remain true? What states and boundary cases change behavior? | Invariants, lifecycle rules, conflicts, and exceptions are unambiguous. |
| 9. Acceptance criteria | What result would make a product owner accept or reject the feature? | Every P0 requirement maps to an observable pass/fail criterion. |
| 10. Release and success | Who receives it first? What must be true before release? How is success recognized? | Release audience, readiness, success, and stop conditions are stated. |
| 11. Dependencies, risks, and decisions | Which business capability or team is required? What could harm users or outcomes? | Blocking dependencies are owned, material product risks have responses, and decisions are recorded. |

After each answer:

1. Update the decision ledger.
2. Check the answer against earlier decisions and the glossary.
3. Apply the scope lock to any new capability it implies.
4. Ask the next highest-impact unresolved question in the same chapter.

If terminology changes, update the repository glossary immediately using product definitions only. Do not turn the glossary into a specification.

## Step 4: Draft the PRD

Use the repository template when one exists. Otherwise use the eleven chapters above. Write the smallest document that fully preserves the confirmed product decisions.

- Use natural language and stable `US-*`, `FR-*`, `BR-*`, and `AC-*` identifiers.
- Describe states by their product meaning; leave storage and code representations to the technical design.
- Keep requirements externally observable and acceptance criteria independently verifiable.
- Record deferred ideas as non-goals or follow-ups, not hidden requirements.
- Mark a genuinely unresolved blocker as open rather than silently choosing it.
- Create no technical design unless the user asks for one.

**Complete when:** every confirmed decision has one home in the PRD and no unconfirmed product behavior is presented as decided.

## Step 5: Audit the result

Run five checks before handoff:

1. **Product lens**: every sentence is understandable and actionable without implementation knowledge.
2. **Scope lock**: every requirement is necessary for the agreed outcome; adjacent ideas remain deferred.
3. **Traceability**: each P0 user story reaches at least one requirement, and each P0 requirement reaches at least one acceptance criterion.
4. **Language**: terms match the glossary and mean the same thing throughout.
5. **Coherence**: flows, permissions, business rules, requirements, and acceptance criteria do not contradict or duplicate each other.

Rewrite any implementation-shaped sentence as a product outcome. Remove empty sections, repeated decisions, and speculative detail rather than padding the document.

**Complete when:** all eleven chapters are either meaningfully complete or explicitly not applicable, every P0 item is traceable, no blocking question remains hidden, and the PRD passes both the product lens and scope lock.

## Step 6: Write and hand off

Save the PRD under the repository's product-document convention, normally `docs/product/<feature>-prd.md`. Validate changed links and formatting. Summarize the product boundary, confirmed decisions, deferred capabilities, and any explicit open question.

**Complete when:** the PRD exists at the intended path, matches the repository template, contains only product requirements, and the user can review it without reading a technical design.
