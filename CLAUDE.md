# ChefAI — Claude Code Instructions

## Permissions
The following `gh` commands are pre-authorized (no confirmation needed):
- `gh issue create` — creating GitHub issues
- `gh api graphql` — GraphQL API queries against GitHub

## Session Memory
On every session start, read `.claude/session-context.md` for current project state.
After completing significant work (features, fixes, architectural changes), update that file with:
- What was changed and why
- Current branch and its purpose
- Any blockers, open questions, or next steps
- Updated project status if milestones changed

---

## Project Overview
**Pocket Chef (ChefAI)** — Offline-first Android recipe management app. Users save recipes, browse a library, generate meal plans, and create grocery lists. Aimed at healthy eating, reducing food waste, and saving time.

- **Android**: Kotlin, Jetpack Compose, Hilt, Room, Ktor client
- **Backend**: Kotlin, Ktor server, PostgreSQL, Exposed DSL, JWT
- **Auth model**: Anonymous-first → optional account upgrade
- **Data model**: Room is local SSOT; backend is authoritative after sync

## Architecture
Hybrid Architecture (Google Modern Android + selective Clean Architecture). See [docs/architecture.md](docs/architecture.md) for full details.

```
UI (Compose + ViewModel + StateFlow)
        ↓ events / ↑ state
Domain (Use Cases, Repository interfaces, Domain models)
        ↓
Data (Repository impls, Room DAOs, Ktor client, Mappers)
```

### Layers
- **UI**: Compose screens, ViewModels exposing `StateFlow<UiState>`. Stateless, previewable composables. No business logic.
- **Domain** (optional): Use cases when logic is reused/complex. Repository interfaces. Pure Kotlin, no Android deps.
- **Data**: Repository implementations, Room entities, network DTOs, mappers at boundaries.

### Package Structure (Feature-Based)
```
com.tenmilelabs.chefai/
├── auth/           (data, domain, ui)
├── recipes/        (data, domain, ui)
├── home/           (ui)
├── mealplans/      (ui)
└── core/           (shared: data, domain, ui, di, util)
```
Rule: start in feature package, move to `core/` only when a second feature needs it.

See [ADR-005](docs/adrs/adr-0005-feature-based-package-structure.md) for full guidelines.

---

## Coding Rules

### Kotlin & Compose
- Idiomatic Kotlin: null-safety, extension functions sparingly
- Compose: `remember{}` only when needed; hoist state; stable parameters; avoid unnecessary recomposition
- ViewModels expose `StateFlow<UiState>` with sealed UI state (Loading, Success, Error)
- Flow/StateFlow for async streams; avoid LiveData
- Prefer constructor injection via Hilt; avoid singletons
- Coroutines: structured concurrency, `Dispatchers.IO` for I/O, never block main

### Architecture Boundaries
- Separate domain models from Room entities & DTOs; map at boundaries
- Repository interfaces in `domain/repository/`, implementations in `data/repository/`
- Keep domain models in `domain/model/`. Never leak Room/DTO types into UI or Domain
- Use mapping functions under `data/mapper/`
- No Android framework dependencies in Domain
- All logic in Domain/Data should be unit-testable without Android
- Domain layer use cases: single-responsibility, stateless, invokable via `operator invoke`
- Repositories expose `Flow` for streams; queries accept plain Kotlin primitives/value objects

### Data & Persistence
- All entities use UUIDv7 IDs (time-sortable). 16-byte blobs in SQLite, native UUIDs in Postgres
- DB schema uses snake_case
- Local persistence: Room with FTS5 enabled
- Entities implement `SyncableEntity` with `syncState`, `updatedAt`, `deletedAt`
- All writes go to Room first; UI reads only from Room flows
- Networking: Ktor client + Kotlinx Serialization; robust error handling; no blocking I/O

### Sync Protocol 
Two-step sync per [ADR-006](docs/adrs/adr-006-anonymous-first-sync.md):
1. `POST /sync/push` — uploads entities with `syncState IN (PENDING, DELETED)`
2. `GET /sync/pull?since=<timestamp>` — fetches backend deltas
- Conflicts: last-writer-wins based on `updatedAt`
- Sync triggers via WorkManager: app foreground, connectivity restored, post-mutation debounce
- Push before pull

### Style
- Logging: Timber; structured messages; avoid noisy logs in production
- Prefer Material 3 components; avoid deprecated Compose APIs
- KDoc on public APIs
- Favor readability and simplicity over performance
- Keep indirection minimal. Introduce layers only when they buy clarity/testability/reuse

---

## Code Generation Patterns

### New Compose Screen
- Stateless UI function + `@Preview`
- ViewModel using Hilt with `StateFlow<UiState>` (sealed: Loading/Success/Error)
- Material3 components, loading + error + empty states
- Navigation event callback
- Unit test for ViewModel
- No business logic in UI; repository used in ViewModel

### Ktor Client Feature
- Kotlinx Serialization for DTOs
- Logging + timeout config
- Suspend network calls
- Map DTO → domain model
- Error handling (network, parse, timeout)
- Unit tests with Ktor MockEngine

### Room Schema Change
- `@Entity` + `@PrimaryKey`
- TypeConverters if needed
- DAO with suspend functions & Flow
- Repository interface + implementation
- Hilt module to provide DAO + DB
- Migration if schema changes
- Test for DAO using in-memory DB

### Testing
- JUnit4; coroutineTestRule + Turbine if Flow used
- Given/When/Then style
- Fake repository or MockK as needed (prefer fakes over mocks)
- Coverage of success + error cases
- Pragmatic duplication of fakes between `test/` and `androidTest/` is acceptable

---

## PR & Change Rules
- Propose diffs, not full file rewrites
- Provide small, reviewable diffs; include rationale in comments
- Don't rename packages or reorganize modules without asking first
- For schema changes: include migration + in-memory DAO tests
- For major architectural decisions: write an ADR in `docs/adrs/` following existing format
- When unsure: ask clarifying questions before large refactors; suggest alternatives with tradeoffs

## Verification After Code Changes
After completing any code change (bug fix, feature, refactor), always run the unit tests as a final verification step:

```
./gradlew :app:testDebugUnitTest
```

- Report the number of tests passed/failed
- If any tests fail, fix them before considering the task done
- Do not ask the user whether to run tests — just run them

---

## Current Gaps (Feb 2026)
| Area | Status                                            |
|------|---------------------------------------------------|
| HomeScreen | Static placeholder data, explore server driven UI |
| Meal Plans | Stub only                                         |
| Conflict resolution | SyncState.CONFLICT defined but unused             |

**Next milestone**: Complete all tasks to prepare for Monstro Demo

---

## Skills (Task Playbooks)

When performing specific tasks, load the matching skill from `.claude/skills/`:

| Task | Skill File |
|------|-----------|
| Build a new Compose screen or component | [`.claude/skills/compose-component.md`](.claude/skills/compose-component.md) |
| Create or modify a ViewModel | [`.claude/skills/viewmodel.md`](.claude/skills/viewmodel.md) |
| Review code (PR or ad-hoc) | [`.claude/skills/code-review.md`](.claude/skills/code-review.md) |
| Modify existing code (bug fix, refactor, feature addition) | [`.claude/skills/update-code.md`](.claude/skills/update-code.md) |

---

## Reference Docs

### Project Docs
- [Architecture Overview](docs/architecture.md)
- [Authentication System](docs/authentication.md)
- [RFC-001: Offline-First Sync](docs/rfcs/rfc-001-offline-first-sync.md)
- [Project Analysis & Roadmap](docs/project-analysis-feb-2026.md)
- ADRs in [docs/adrs/](docs/adrs/)

### Claude Docs
- [Decisions (ADR Index)](docs/claude/decisions.md)
- [Code Conventions](docs/claude/conventions.md)
- [Gotchas & Lessons](docs/claude/gotchas.md)
- [Session Onboarding](docs/claude/onboarding.md)
