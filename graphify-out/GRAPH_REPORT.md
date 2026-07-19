# Graph Report - .  (2026-07-19)

## Corpus Check
- 129 files · ~53,606 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 450 nodes · 461 edges · 64 communities (51 shown, 13 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 49 edges (avg confidence: 0.91)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Test Driven Development
- Platform Architecture Documents
- Predictable Skill Design
- Tenant Domain Model
- Deep Module Design
- Current Tenant Security
- Domain Modeling Practices
- Repository Skill Setup
- Implementation Readiness Flow
- Idea to Review Flow
- Issue Triage Workflow
- JWT Tenant Conversion
- Integration Event Envelope
- Product Requirements Workflow
- Prototype Design Workflow
- Stateful Teaching System
- Wayfinding Ticket System
- Architecture Improvement Workflow
- Quality and CI Gates
- Writing Great Skills
- Codebase Design Methods
- Learning Lesson Design
- Repository Operating Rules
- Local Infrastructure Stack
- Gateway Architecture Tests
- IAM Architecture Tests
- Messaging Architecture Tests
- Security Architecture Tests
- Primary Source Research
- Merge Conflict Resolution
- Human Loop Script
- Gradle Wrapper Script
- Gateway Application Entry
- Gateway Application Tests
- IAM Application Entry
- IAM Application Tests
- Learning Memory Strength
- Skill Relevance Hygiene
- Missing Tenant Exception
- ADR Qualification Policy
- Learning Community Wisdom
- Learning Skill Feedback
- Expand Contract Refactoring
- Triage Label Roles
- Dependency Deepening Categories
- Handoff Agent Interface
- Teaching Notes Practice

## God Nodes (most connected - your core abstractions)
1. `TenantJwtAuthenticationConverter` - 8 edges
2. `Setup Matt Pocock Skills` - 8 edges
3. `Organization Directory Technical Design` - 8 edges
4. `Specification Synthesis` - 7 edges
5. `Tenant` - 6 edges
6. `User` - 6 edges
7. `Employee` - 6 edges
8. `Agent Brief` - 6 edges
9. `Triage State Machine` - 6 edges
10. `Information Hierarchy` - 6 edges

## Surprising Connections (you probably didn't know these)
- `ADR Format` --conceptually_related_to--> `Decision Documentation Policy`  [AMBIGUOUS]
  .agents/skills/domain-modeling/ADR-FORMAT.md → AGENTS.md
- `Decision Documentation Policy` --conceptually_related_to--> `Business Capability Service Seam Policy`  [INFERRED]
  AGENTS.md → README.md
- `Diagnosing Bugs` --references--> `CloudForge Platform`  [EXTRACTED]
  .agents/skills/diagnosing-bugs/SKILL.md → CONTEXT.md
- `Single and Multi-Context Layout` --references--> `CloudForge Platform`  [INFERRED]
  .agents/skills/domain-modeling/CONTEXT-FORMAT.md → CONTEXT.md
- `Grill with Docs` --conceptually_related_to--> `CloudForge Platform`  [INFERRED]
  .agents/skills/grill-with-docs/SKILL.md → CONTEXT.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **CloudForge Tenant Authorization Model** — context_tenant, context_current_tenant, context_user, context_session, context_employee, context_tenant_role, context_permission [EXTRACTED 1.00]
- **Deep Module Design Vocabulary** — _agents_skills_codebase_design_skill_module, _agents_skills_codebase_design_skill_interface, _agents_skills_codebase_design_skill_depth, _agents_skills_codebase_design_skill_seam, _agents_skills_codebase_design_skill_adapter, _agents_skills_codebase_design_skill_leverage, _agents_skills_codebase_design_skill_locality [EXTRACTED 1.00]
- **Idea to Ship Skill Flow** — _agents_skills_grill_with_docs_skill_grill_with_docs, _agents_skills_grilling_skill_grilling, _agents_skills_domain_modeling_skill_domain_modeling, _agents_skills_handoff_skill_handoff, _agents_skills_code_review_skill_code_review [EXTRACTED 1.00]
- **Question-Driven Prototype Branches** — _agents_skills_prototype_skill_prototype, _agents_skills_prototype_logic_logic_prototype, _agents_skills_prototype_ui_ui_prototype [EXTRACTED 1.00]
- **Development Readiness Pipeline** — _agents_skills_ready_development_skill_evidence_map, _agents_skills_ready_development_skill_scope_lock, _agents_skills_ready_development_skill_seven_design_gates, _agents_skills_ready_development_skill_progressive_technical_design, _agents_skills_ready_development_skill_development_readiness_review, _agents_skills_ready_development_skill_issue_publication [EXTRACTED 1.00]
- **Repository Engineering Skill Configuration** — _agents_skills_setup_matt_pocock_skills_skill_setup_matt_pocock_skills, _agents_skills_setup_matt_pocock_skills_domain_domain_docs, _agents_skills_setup_matt_pocock_skills_issue_tracker_github_github_issue_tracker, _agents_skills_setup_matt_pocock_skills_issue_tracker_gitlab_gitlab_issue_tracker, _agents_skills_setup_matt_pocock_skills_issue_tracker_local_local_markdown_issue_tracker, _agents_skills_setup_matt_pocock_skills_triage_labels_triage_labels [EXTRACTED 1.00]
- **Teaching Workspace Artifacts** — _agents_skills_teach_mission_format_learning_mission, _agents_skills_teach_resources_format_curated_resources, _agents_skills_teach_learning_record_format_learning_record, _agents_skills_teach_skill_lesson, _agents_skills_teach_skill_reference_document, _agents_skills_teach_skill_teaching_notes [EXTRACTED 1.00]
- **Wayfinder Ticket Types** — _agents_skills_wayfinder_skill_decision_ticket, _agents_skills_wayfinder_skill_research_ticket, _agents_skills_wayfinder_skill_prototype_ticket, _agents_skills_wayfinder_skill_grilling_ticket, _agents_skills_wayfinder_skill_task_ticket [EXTRACTED 1.00]
- **Predictable Skill Design Axes** — _agents_skills_writing_great_skills_glossary_predictability, _agents_skills_writing_great_skills_glossary_model_invoked, _agents_skills_writing_great_skills_glossary_information_hierarchy, _agents_skills_writing_great_skills_glossary_leading_word, _agents_skills_writing_great_skills_glossary_single_source_of_truth [EXTRACTED 1.00]
- **CloudForge Feature Delivery Chain** — docs_templates_prd_template_prd_template, docs_templates_technical_design_template_technical_design_template, docs_agents_issue_tracker_github_issue_tracker, _github_workflows_ci_test_ci_check [EXTRACTED 1.00]
- **CloudForge Application and Shared Module Map** — docs_architecture_modules_iam_service, docs_architecture_modules_gateway_bff, docs_architecture_modules_organization_service, docs_architecture_modules_shared_security, docs_architecture_modules_shared_messaging [EXTRACTED 1.00]
- **User Employee and Session Integration** — docs_architecture_user_identity_technical_design_session_user_v1, docs_architecture_user_identity_technical_design_employee_binding, docs_architecture_organization_service_technical_design_session_user_context, docs_architecture_organization_service_technical_design_organization_service_technical_design [EXTRACTED 1.00]

## Communities (64 total, 13 thin omitted)

### Community 0 - "Test Driven Development"
Cohesion: 0.07
Nodes (30): TDD Skill Interface, Dependency Injection for Mockability, SDK-Style Interface, System Boundary Mocking, Good Test, Implementation-Coupled Test, Red-Green Loop, Tautological Test (+22 more)

### Community 1 - "Platform Architecture Documents"
Cohesion: 0.08
Nodes (30): Product and Technical Document Authority, CloudForge Domain Documentation Rules, Gateway BFF, IAM Service, CloudForge Module Map, Organization Service, Shared Messaging Module, Shared Security Module (+22 more)

### Community 2 - "Predictable Skill Design"
Cohesion: 0.08
Nodes (25): Branch, Co-location, Cognitive Load, Completion Criterion, Context Load, Context Pointer, Description, Duplication (+17 more)

### Community 3 - "Tenant Domain Model"
Cohesion: 0.13
Nodes (24): Collaborator, Current Tenant, Employee, Event Envelope, External Contact, Integration Contract, Integration Event, Ordered Subscription (+16 more)

### Community 4 - "Deep Module Design"
Cohesion: 0.10
Nodes (22): Bug Diagnosis On-Ramp, Ports and Adapters, Replace Don't Layer Testing, Seam Discipline, Adapter, Depth, Implementation, Interface (+14 more)

### Community 5 - "Current Tenant Security"
Cohesion: 0.14
Nodes (9): AfterEach, CurrentTenant, Override, SecurityContextCurrentTenant, TenantId, Jwt, Test, SecurityContextCurrentTenantTests (+1 more)

### Community 6 - "Domain Modeling Practices"
Cohesion: 0.11
Nodes (19): ADR Format, Domain Modeling Agent Interface, Canonical Domain Terms, CONTEXT.md Format, Single and Multi-Context Layout, Domain Modeling, Inline Glossary Updates, Ubiquitous Language Discipline (+11 more)

### Community 7 - "Repository Skill Setup"
Cohesion: 0.13
Nodes (17): Setup Matt Pocock Skills Agent Metadata, ADR Conflict Disclosure, Domain Docs, Domain Glossary Vocabulary, Multi-Context Domain Layout, Single-Context Domain Layout, GitHub Issue Tracker, GitHub Wayfinding Operations (+9 more)

### Community 8 - "Implementation Readiness Flow"
Cohesion: 0.12
Nodes (16): Implement Agent Metadata, Implement Skill, Implementation Workflow, Ready Development Agent Metadata, Development Issue Body, Issue Slicing, Issue Sizing Gate, Tracer Bullet Slice (+8 more)

### Community 9 - "Idea to Review Flow"
Cohesion: 0.14
Nodes (15): Ask Matt Agent Interface, Ask Matt, Context Hygiene, Idea to Ship Main Flow, Smart Zone, Code Review Agent Interface, Code Review, Fixed Point Diff (+7 more)

### Community 10 - "Issue Triage Workflow"
Cohesion: 0.13
Nodes (15): Agent Brief Acceptance Criteria, Agent Brief, Behavioral Agent Brief, Durability over Precision, Agent Brief Scope Boundaries, Triage Skill Interface, Rejected-Feature Concept Deduplication, Rejected-Feature Institutional Memory (+7 more)

### Community 11 - "JWT Tenant Conversion"
Cohesion: 0.23
Nodes (9): AbstractAuthenticationToken, Converter, GrantedAuthority, JwtAuthenticationConverter, Jwt, Override, TenantJwtAuthenticationConverter, Test (+1 more)

### Community 12 - "Integration Event Envelope"
Cohesion: 0.20
Nodes (6): EventEnvelope, EventScope, PLATFORM, TENANT, EventEnvelopeTests, Test

### Community 13 - "Product Requirements Workflow"
Cohesion: 0.14
Nodes (14): Writing PRD Agent Metadata, Chapter-by-Chapter Product Interview, PRD Traceability, Product Lens, PRD Scope Lock, Writing PRDs, GitHub Issue Tracker, GitHub Wayfinding Operations (+6 more)

### Community 14 - "Prototype Design Workflow"
Cohesion: 0.19
Nodes (13): Prototype Agent Metadata, Lightweight TUI, Logic Prototype, Portable Logic Module, Validated Logic Capture, Prototype Skill, Prototype Capture Workflow, Throwaway Prototype (+5 more)

### Community 15 - "Stateful Teaching System"
Cohesion: 0.18
Nodes (13): Teach Skill Interface, Canonical Teaching Glossary, Understanding-Gated Glossary Term, Evidence of Understanding, Learning Record, Learning Record Supersession, Concrete Learning Outcome, Learning Mission (+5 more)

### Community 16 - "Wayfinding Ticket System"
Cohesion: 0.18
Nodes (11): Wayfinder Skill Interface, Decision Ticket, Wayfinder Destination, Grilling Ticket, Wayfinder Map Out of Scope, Plan, Don't Do, Prototype Ticket, Research Ticket (+3 more)

### Community 17 - "Architecture Improvement Workflow"
Cohesion: 0.22
Nodes (10): Improve Codebase Architecture Agent Metadata, Architecture Report Vocabulary, Before and After Architecture Diagrams, Architecture Candidate Card, HTML Report Format, Deepening Opportunities, Deletion Test, Architecture Grilling Loop (+2 more)

### Community 18 - "Quality and CI Gates"
Cohesion: 0.25
Nodes (8): CI Check Workflow, Repository Style Check, Gateway and IAM Service Checks, Shared Module Checks, Apache 2.0 Java License Header, Code Style and Quality Standards, Code Dependency Boundaries, Mandatory Quality Gate

### Community 19 - "Writing Great Skills"
Cohesion: 0.33
Nodes (6): Writing Great Skills Agent Metadata, Skill Information Hierarchy, Leading Words, Progressive Disclosure, Skill Predictability, Writing Great Skills

### Community 20 - "Codebase Design Methods"
Cohesion: 0.50
Nodes (5): Codebase Design Agent Interface, Deepening, Design It Twice, Parallel Interface Design, Codebase Design

### Community 21 - "Learning Lesson Design"
Cohesion: 0.40
Nodes (5): Knowledge, Lesson, Reference Document, Reusable Lesson Component, Zone of Proximal Development

### Community 22 - "Repository Operating Rules"
Cohesion: 0.40
Nodes (5): CloudForge Repository Structure, Graphify Workflow, Quality Merge Gate, Repository Guidelines, Security and Configuration Policy

### Community 23 - "Local Infrastructure Stack"
Cohesion: 0.40
Nodes (5): Local Infrastructure, PostgreSQL Service, RabbitMQ Service, Redis Service, Local Development Lifecycle

### Community 24 - "Gateway Architecture Tests"
Cohesion: 0.60
Nodes (3): GatewayArchitectureTests, AnalyzeClasses, ArchRule

### Community 25 - "IAM Architecture Tests"
Cohesion: 0.60
Nodes (3): IamArchitectureTests, AnalyzeClasses, ArchRule

### Community 26 - "Messaging Architecture Tests"
Cohesion: 0.60
Nodes (3): AnalyzeClasses, ArchRule, MessagingArchitectureTests

### Community 27 - "Security Architecture Tests"
Cohesion: 0.60
Nodes (3): AnalyzeClasses, ArchRule, SecurityArchitectureTests

### Community 28 - "Primary Source Research"
Cohesion: 0.50
Nodes (4): Research Agent Metadata, Background Research Agent, Primary Source Research, Research Skill

### Community 29 - "Merge Conflict Resolution"
Cohesion: 0.50
Nodes (4): Resolving Merge Conflicts Agent Metadata, Intent-Preserving Conflict Resolution, Merge or Rebase Completion, Resolving Merge Conflicts Skill

### Community 30 - "Human Loop Script"
Cohesion: 0.83
Nodes (3): capture(), hitl-loop.template.sh script, step()

### Community 31 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 36 - "Learning Memory Strength"
Cohesion: 0.67
Nodes (3): Desirable Difficulty, Fluency Strength, Storage Strength

### Community 37 - "Skill Relevance Hygiene"
Cohesion: 0.67
Nodes (3): No-Op, Relevance, Sediment

## Ambiguous Edges - Review These
- `Decision Documentation Policy` → `ADR Format`  [AMBIGUOUS]
  .agents/skills/domain-modeling/ADR-FORMAT.md · relation: conceptually_related_to

## Knowledge Gaps
- **131 isolated node(s):** `TENANT`, `PLATFORM`, `CloudForge Repository Structure`, `Quality Merge Gate`, `Graphify Workflow` (+126 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Decision Documentation Policy` and `ADR Format`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `Grill with Docs` connect `Domain Modeling Practices` to `Idea to Review Flow`?**
  _High betweenness centrality (0.010) - this node is a cross-community bridge._
- **Why does `CloudForge Platform` connect `Domain Modeling Practices` to `Deep Module Design`?**
  _High betweenness centrality (0.008) - this node is a cross-community bridge._
- **Why does `Diagnosing Bugs` connect `Deep Module Design` to `Domain Modeling Practices`?**
  _High betweenness centrality (0.007) - this node is a cross-community bridge._
- **What connects `TENANT`, `PLATFORM`, `CloudForge Repository Structure` to the rest of the system?**
  _131 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Test Driven Development` be split into smaller, more focused modules?**
  _Cohesion score 0.06666666666666667 - nodes in this community are weakly interconnected._
- **Should `Platform Architecture Documents` be split into smaller, more focused modules?**
  _Cohesion score 0.07586206896551724 - nodes in this community are weakly interconnected._