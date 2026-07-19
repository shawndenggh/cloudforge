# Issue Slicing

Read this reference only after the technical design has passed its development-readiness review.

## Slice by tracer bullet

Make each Issue one small vertical change that produces an observable result through one primary seam. A good Issue can be tested, reviewed, merged, deployed, and reverted without requiring reviewers to understand the whole feature at once.

Prefer this order:

1. a compatibility prerequisite only when safe rollout requires it;
2. the thinnest end-to-end behavior through the chosen seam;
3. additional rules and failure behaviors as separate vertical slices;
4. integrations, import, audit, or operations only when they are in scope;
5. final cross-slice verification when it adds behavior not already proven.

A standalone database, interface, or infrastructure Issue earns its place only when it is independently verifiable and must land before a behavior slice for compatibility or deployment safety. Otherwise keep the enabling work inside the tracer bullet that first uses it.

## Sizing gate

Every proposed Issue must satisfy all of these:

- delivers one caller- or user-observable capability, or one required compatibility prerequisite;
- centers on one agreed test seam and a short red-to-green sequence;
- has explicit dependencies and can start when those dependencies close;
- changes only the modules necessary for its result;
- has acceptance criteria that can pass before later Issues exist;
- can be reviewed and reverted as one focused Pull Request;
- contains no deferred cleanup or adjacent product capability.

Split again when an Issue contains multiple outcomes, crosses unrelated seams, has independent rollback decisions, or cannot be reviewed without mentally simulating later work.

## Issue body

Use the repository's tracker conventions and inspect existing Issues for duplicates. Give every Issue this information:

```markdown
## Goal

<one observable result>

## Requirement links

- PRD: <FR/BR/AC identifiers and link>
- Technical design: <chapter, API, model, flow, and TC identifiers>

## Scope

- <behavior included in this Issue>

## Codebase evidence

- <current modules, interfaces, tests, or migrations this slice builds on>

## Test-first slices

1. Red: <failing behavior test through the agreed seam>
2. Green: <minimum implementation that makes it pass>
3. Repeat only for another behavior required by this same outcome.

## Acceptance criteria

- [ ] <independently observable completion condition>

## Dependencies

- Blocked by: <Issue title/link or none>

## Non-goals

- <adjacent work intentionally excluded>
```

Mention likely code touchpoints as evidence, not as a frozen file checklist. The technical design remains the source of truth for contracts and architecture; Issues point to it instead of copying whole sections.

## Approval and publication

Before tracker writes:

1. show the ordered Issue titles, observable result, primary seam, and dependency for each;
2. explain why each boundary is independently reviewable;
3. ask for explicit approval of the Issue plan.

After approval, follow the repository's issue-tracker instructions. Link Issues to the existing PRD or feature tracker and use native sub-Issue and dependency relationships when supported. Apply `ready-for-agent` when an Issue contains every required decision and can be completed autonomously; apply `ready-for-human` only when its completion genuinely requires a person.

Verify every created Issue, dependency, label, and link. Report the first unblocked Issue as the recommended next development task, then stop.
