# ChefAI Architecture Overview

_Last updated: November 2025_

ChefAI follows a **Hybrid Architecture** based on Google’s Modern Android Architecture, selectively enhanced with **Clean Architecture principles**.  
The goal is to balance **developer velocity** (for a solo engineer) with **clarity, testability, and scalability** for future growth or collaboration.

---

## 🧩 Layer Overview

```
                UI (Compose, ViewModels)
                            ↓
Domain (Use Cases, Domain Entities, Repository Interfaces)
                            ↓
 Data (Repository Implementations, Local/Remote, Mappers)
```

![Diagram](img/chefai-architecture-diagram.png "Data Flow Architecture")

![Architecture Details](img/ChefAI-Architecure.png)

### UI Layer
- Uses Jetpack Compose and `ViewModel` with `StateFlow` for state management.
- Emits **UI events** upward and receives **state updates** downward.
- Contains no data logic or persistence dependencies.

### Domain Layer
- Optional layer used when logic grows in complexity or reuse.
- Holds:
    - **Domain models (entities)** — pure Kotlin, representing business meaning.
    - **Use Cases** — reusable, testable orchestration of repositories or domain logic.
    - **Repository interfaces** — abstractions implemented in the Data layer.
- Free of Android or I/O dependencies.

### Data Layer
- Contains repository implementations, data sources (Room, Retrofit/Ktor), DTOs, and mappers.
- Owns the **single source of truth** for persisted or cached data.
- Responsible for coordinating between network, local storage, and mapping to/from domain.

---

## 🔄 Unidirectional Data Flow (UDF)

```
UI → Domain → Data
↑               ↓
--< State Flow <--
```

- **Events flowdown from UI → Domain → Data** user actions, triggers, or intents.
- **State flows up from Data → Domain → UI** data updates or UI models emitted via `Flow`/`StateFlow`.
- Each data type has a **single source of truth** (usually a repository).

---

## 🧱 Entities and Models

| Layer | Type | Example | Notes |
|-------|------|----------|-------|
| Domain | Entity / Business Model | `Recipe` | Core business truth |
| Network | DTO | `RecipeResponse` | Mirrors API schema |
| Database | Persistence Model | `RecipeEntity` | Matches Room table |
| UI | UI Model | `RecipeUi` | Tailored for presentation |

Mappers convert between these layers under `data/mapper/` and `ui/mappers/`.

---

## ⚙️ Dependency Direction

`UI → Domain → Data`

- Domain defines interfaces; Data implements them.
- No Android or storage dependencies are allowed in the Domain layer.
- ViewModels and UseCases are constructed via dependency injection (Hilt/Koin in future).

---

## ✅ Design Principles

- **Simplicity First:** introduce layers only when needed.
- **Testability:** all business logic must be unit-testable without Android.
- **Separation of Concerns:** UI, business rules, and data persistence stay isolated.
- **Explicit Data Flow:** no mutable shared state across layers.
- **Evolution-friendly:** easily migrate to multi-module (e.g. `:domain`, `:data`) if app grows.

---

## 📄 Related Documents

- [`/AGENTS.md`](../AGENTS.md) — formal rules for AI/code generation.
- [`/docs/decisions/adr-0001-hybrid-architecture-choice.md`](decisions/adr-0001-hybrid-architecture-choice.md) — rationale for choosing this hybrid architecture.