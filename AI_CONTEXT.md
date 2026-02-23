# AI Context: ChefAI Android App

ChefAI is an offline-first recipe management app built with Kotlin, Jetpack Compose, and Ktor.  
Users can save recipes, browse a library, generate weekly meal plans, and get grocery lists.

## Architectural Goals
- Kotlin-only codebase
- MVVM with Repository pattern
- Offline-first sync model
- Unidirectional Data Flow
- Local DB as SSOT
- Clear domain/data separation
- Stateless, preview-friendly Composables
- Scalable navigation graph
- Testability across all layers

## Tech Stack
- UI: Jetpack Compose + Material3 + Jetpack Navigation
- DI: Hilt
- Async: Kotlin Coroutines + Flow + StateFlow
- Persistence: Room + TypeConverters
- Networking: Ktor + Kotlinx Serialization
- Logging: Timber + SLF4J/Logback
- Testing: JUnit + CoroutineTest + Turbine later

## Core Modules / Layers
- `data/` → Room, DAOs, DTOs, network service, repository impl
- `domain/` → Models & interfaces
- `ui/` → Compose screens, ViewModels, navigation
- `di/` → Hilt modules
- Future: `/ai/` module for meal planner & LLM integration

## Current Key Features
- User-managed personal recipe collection (create, edit, delete)
- Browse library recipes with pagination (Paging 3)
- View recipe details with tabs (overview, ingredients, instructions)
- Store images & metadata locally
- User authentication (login, register, token refresh) via Ktor → backend
- Secure token storage (EncryptedSharedPreferences)
- Feature-based package structure (auth/, recipes/, home/, mealplans/, core/)

## Current Gaps (Feb 2026)
- Anonymous-first usage NOT implemented (login required)
- Sync NOT implemented (no Outbox, no SyncWorker, no WorkManager)
- RecipesViewModel and DefaultRecipeRepository use hardcoded test user UUID
- HomeScreen uses static placeholder data (no repository)
- MealPlans is stub only

## 🔄 Sync and Data Layer Guidelines
- Local persistence uses SQLite via Room with FTS5 enabled.
- All entities use backend-generated UUIDv7 IDs (time-sortable).
- Do not create auto-increment IDs or entity prefixes.
- Sync is two-step:
    1. POST /sync/push uploads local outbox entries.
    2. GET /sync/pull?since=<timestamp> fetches backend deltas.
- Client tracks lastSyncedAt and syncState per entity.
- Always perform DB writes inside transactions and mark successful syncs as SYNCED.
- Conflicts resolved by last-writer-wins based on updatedAt.
- WorkManager runs sync on startup, connectivity, or local mutation events.


## Upcoming Features
- Meal planner (AI-assisted)
- Grocery list generation
- Remote sync with backend
- Recipe search & filters

## Code Style Requirements
- Follow idiomatic Kotlin & Compose patterns
- ViewModels expose `StateFlow<UiState>` and handle logic
- UI screens are stateless + Previewable
- Repository interfaces in domain layer
- Always use dependency injection (Hilt)
- Persistence models separated from domain models
- Handle offline & error states gracefully
- Avoid unnecessary recompositions

## What AI Should Do
- Propose diffs, not full file rewrites
- Follow project patterns and libraries listed above
- Ask clarifying questions if context is missing
- Provide tests with new features when feasible
