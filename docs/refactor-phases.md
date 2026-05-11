# Refactor Phases Playbook

This document is a session-friendly checklist to execute the refactor in small, mergeable steps.
Use one phase per PR.

## How to use this playbook

For each phase:
1. Create branch.
2. Implement only the listed scope.
3. Run validation commands.
4. Commit with a phase-specific message.
5. Open PR.

---

## Phase 0 — Baseline and guardrails

**Goal**: Establish confidence checks and clear invariants before deeper changes.

**Scope**
- Document baseline checks and generated-output expectations.
- Ensure CI/runtime assumptions are aligned (Java/Node/tooling).

**Done when**
- There is a quality-gates document.
- README points to the guardrails.
- Baseline build checks are explicit and reproducible.

**Validation**
- `./gradlew test`
- `cd web && npm run build`

---

## Phase 1 — Single source of truth for season identity

**Goal**: Define active season name and mode (`zaal`/`veld`) in one place and consume it everywhere.

**Scope**
- Backend: central season config object.
- Frontend: consume generated/central season info instead of scattered literals.
- Keep current behavior unchanged.

**Done when**
- Active season and mode come from one source.
- Backend and frontend both read that source.

**Validation**
- `./gradlew run`
- `cd web && npm run build`

---

## Phase 2 — Generated output contracts in CI

**Goal**: Guard output stability and historical rows.

**Contracts**
1. Equal input => equal generated match-set output.
2. Let `D` be earliest date changed in `matches/` by a PR; generated rows before `D` must not change.

**Scope**
- Determinism check script.
- Earliest-date guard script.
- PR opt-out label: `changes generated match output`.

**Done when**
- CI enforces both contracts by default.
- Labeled PRs can intentionally skip contract checks.

**Validation**
- `./.ci/check_predict_determinism.sh`
- `./.ci/check_generated_csv_guard.py <base-ref>`

---

## Phase 3 — Backend architecture split

**Goal**: Reduce coupling and improve testability.

**Scope**
- Introduce layered boundaries:
  - `domain` (pure rules)
  - `application` (pipelines/use-cases)
  - `infrastructure` (file/scraper/export)
- Keep entrypoints thin.

**Done when**
- Core logic can run in tests without direct file/network dependencies.
- Public outputs are unchanged for same input.

**Validation**
- `./gradlew clean test`
- `./gradlew run`

---

## Phase 4 — Kotlin/TypeScript season-rule parity

**Goal**: Keep backend and frontend season logic aligned by design.

Detailed design:
- [phase-4-declarative-pd-tdd.md](phase-4-declarative-pd-tdd.md)

**Scope**
- Move rule definitions to shared declarative format.
- Consume same rule source from Kotlin and TS simulator.
- Add parity checks.

**Done when**
- Rule changes are made once and reflected in both stacks.

**Validation**
- `./gradlew predict --args="2026-01-01"`
- `cd web && npm run build`
- parity test command(s)

---

## Phase 5 — Frontend separation of concerns

**Goal**: Cleaner state management and data access.

**Scope**
- Move HTTP/parsing into services.
- Keep Pinia stores thin (state + orchestration).
- Standardize loading/error handling.

**Done when**
- Stores do not contain low-level parsing/network logic.

**Validation**
- `cd web && npm run build`

---

## Phase 6 — Fast CI lane

**Goal**: Keep PR feedback fast while preserving confidence.

**Scope**
- Split fast PR checks vs heavier scheduled checks.
- Cache dependencies and avoid redundant work.

**Done when**
- PR checks remain quick and deterministic contracts are still enforced.

**Validation**
- CI runtime tracking on PR workflow

---

## Phase 7 — Presentation-ready polish

**Goal**: Make repo externally presentable for contributors and demos.

**Scope**
- Final docs pass.
- Contributor runbook.
- Release sanity checklist.

**Done when**
- A new contributor can run, understand, and validate the project quickly.

---

## Suggested PR order

1. Phase 0
2. Phase 1
3. Phase 2
4. Phase 3
5. Phase 4
6. Phase 5
7. Phase 6
8. Phase 7
