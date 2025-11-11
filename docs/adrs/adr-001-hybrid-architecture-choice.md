# ADR 0001 – Adopt Hybrid Architecture (Google Modern + Clean Principles)

**Date:** November 2025  
**Status:** Accepted  
**Author:** Jose Mucientes

---

## Context

ChefAI is currently a single-developer project but aims for maintainability and production standard.  
Google’s *Modern Android Architecture* (UI + Data + optional Domain) provides strong developer velocity and idiomatic Compose integration.  
Classic *Clean Architecture* introduces strict layering and dependency inversion, which benefits larger teams, testability, and multi-platform consistency but adds overhead.

---

## Decision

Adopt a **Hybrid Architecture** that uses Google’s Modern Android recommendations as the foundation, enhanced with Clean Architecture principles **selectively**:

- UI layer (Compose + ViewModel) follows Google’s unidirectional data flow.
- Domain layer is optional, used for reusable or complex logic, but isolated from Android and I/O.
- Repository interfaces live in the Domain layer; implementations in the Data layer.
- Data layer owns persistence and network orchestration, provides `Flow`-based streams.
- Use Mappers to isolate models between layers.
- Domain models represent the business truth; network and database have their own DTOs/entities.
---

## Consequences

### ✅ Benefits
- Minimal boilerplate and faster iteration for a solo developer.
- Compatible with Google samples and best practices.
- Enables future modularization (domain/data separation).
- Provides a realistic, senior-level architecture story for interviews.
- Maintains clear test boundaries without over-engineering.

### ⚠️ Trade-offs
- Some duplication (Domain vs DTO vs Room models).
- Slightly looser inversion than strict Clean Architecture.
- Requires discipline to avoid pushing logic back into the UI or Data layers.

### 🚀 Future Evolution
- Introduce separate Gradle modules (`:domain`, `:data`, `:app`) once codebase grows.
- Add Hilt for dependency injection.
- Add Room + Retrofit/Ktor concrete data sources.
- Expand documentation with further ADRs (entities, data flow, testing strategy).

---

## References

- [Google – Guide to App Architecture](https://developer.android.com/topic/architecture)
- [Google – Jetpack Compose Architecture](https://developer.android.com/develop/ui/compose/architecture)
- [Clean Architecture (Robert C. Martin)](https://8thlight.com/blog/uncle-bob/2012/08/13/the-clean-architecture.html)
- Philipp Lackner – *Explaining Google’s Guide to App Architecture in Simple Terms* (YouTube)