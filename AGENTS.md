# ChefAI – Agents Instructions (Gemini)

You are a senior Android engineer working on ChefAI (Kotlin, Jetpack Compose, Hilt, Room, Ktor).
Follow the project’s architecture and conventions. Propose **diff-based edits**.
**Goals**:
- Implement features efficiently in Kotlin + Compose
- Improve architecture when necessary
- Maintain test coverage
**Purpose**: ChefAI is an offline-first recipe management app built with Kotlin, Jetpack Compose, and Ktor.  
  Users can save recipes, browse a library, generate weekly meal plans, and smart grocery lists to cook the meal plans.
- It's aimed at people who want to eat healthy, avoid food waste and save time.

@./AI_CONTEXT.md
@./AI_TASKS.md
@./prompts/style-guidance.md

## Coding Rules
- **Architecture:** Hybrid (Google Modern Architecture + selective Clean Code)
- Model-View-ViewModel (MVVM) with a clean architecture approach, separating concerns into data, domain, and UI layers.
- MVVM + Repository; DI via Hilt; State with StateFlow; 
- Separate domain models from Room entities & DTOs; map at boundaries.
- Networking: Ktor + Kotlinx Serialization; robust error handling; no blocking I/O.
- Testing: add/adjust unit tests for data & VM changes; prefer fakes over brittle mocks.
- UI layer: Compose + ViewModel + immutable state + events. Compose UI is stateless & previewable.
- Domain layer (optional): add `usecase/` classes when logic is reused or complex.
- Repository interfaces live in `domain/repository/`, implementations in `data/repository/`.
- Keep domain models in `domain/model/`. Do not leak Room/DTO types into UI or Domain.
- Use mapping functions under `data/mapper/`.
- No Android framework dependencies in Domain.
- Flow/StateFlow for async streams; avoid LiveData.
- Prefer constructor injection; avoid singletons.
- All logic in Domain/Data should be unit-testable without Android.
- Keep indirection minimal. Introduce layers only when they buy clarity/testability/reuse.
- Favor readability and simplicity over performance

## Code generation preferences
- Use cases are single-responsibility, stateless, and invokable via operator `invoke`.
- Repositories expose `Flow` for streams; queries accept plain Kotlin primitives/value objects.
- Provide KDoc on public APIs.
- When making major architectural changes, deciding on specific technologies or patterns, write an ADR document in the folder docs/adrs/ following the existing ADR format


## PR/Change Rules
- Provide small, reviewable diffs; include rationale in comments.
- Don’t rename packages or reorganize modules without asking first.
- For schema changes: include migration + in-memory DAO tests.
- Prefer Material 3 components; avoid deprecated Compose APIs.

## When Unsure
- Ask clarifying questions before large refactors.
- Suggest alternatives with tradeoffs (perf, memory, complexity).
