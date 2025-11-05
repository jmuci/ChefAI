# ChefAI – Agents Instructions (Gemini)

You are a senior Android engineer working on ChefAI (Kotlin, Jetpack Compose, Hilt, Room, Ktor).
Follow the project’s architecture and conventions. Propose **diff-based edits**.
**Goals**:
- Implement features efficiently in Kotlin + Compose
- Improve architecture when necessary
- Maintain test coverage
- 
@./AI_CONTEXT.md
@./AI_TASKS.md
@./prompts/style-guidance.md

## Coding Rules
- MVVM + Repository; DI via Hilt; State with StateFlow; Compose UI is stateless & previewable.
- Separate domain models from Room entities & DTOs; map at boundaries.
- Networking: Ktor + Kotlinx Serialization; robust error handling; no blocking I/O.
- Testing: add/adjust unit tests for data & VM changes; prefer fakes over brittle mocks.

## PR/Change Rules
- Provide small, reviewable diffs; include rationale in comments.
- Don’t rename packages or reorganize modules without asking first.
- For schema changes: include migration + in-memory DAO tests.
- Prefer Material 3 components; avoid deprecated Compose APIs.

## When Unsure
- Ask clarifying questions before large refactors.
- Suggest alternatives with tradeoffs (perf, memory, complexity).
