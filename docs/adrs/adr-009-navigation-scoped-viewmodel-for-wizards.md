# ADR 009 – Navigation-Scoped ViewModel for Multi-Screen Wizard Flows

**Date:** March 2026
**Status:** Accepted
**Author:** Jose Mucientes

---

## Context

The meal planning feature introduces a multi-screen wizard (3 steps: Basics → Preferences → Extras) that collects user preferences before creating a meal plan. State must be shared across all three screens and survive navigation between them, but should be destroyed when the user exits the wizard entirely (back to the Meal Plans list).

Three patterns were considered:

1. **Single ViewModel per screen** — each screen has its own VM; state is passed via `NavBackStackEntry` arguments between steps. Simple but breaks down when shared state grows: preferences form one cohesive object and serialising/deserialising it across navigation arguments is fragile.

2. **Parent Activity / Application-scoped ViewModel** — survives the wizard but leaks beyond it. State is never cleaned up until the app is killed. Not appropriate here.

3. **Navigation-graph-scoped ViewModel** — the wizard screens are wrapped in a nested `NavGraph`. The ViewModel is retrieved via `navController.getBackStackEntry(nestedGraphRoute)`, scoping its lifetime to the nested graph. When the user exits the graph (completes or cancels), the ViewModel is cleared automatically.

---

## Decision

Use a **navigation-graph-scoped ViewModel** for multi-screen wizard flows.

- Define a nested `NavGraph` for the wizard in `ChefAINavGraph.kt`.
- All wizard screens obtain the shared ViewModel via:
  ```kotlin
  val parentEntry = navController.getBackStackEntry(MealPlanDestinations.CREATE_GRAPH)
  val viewModel: CreateMealPlanViewModel = hiltViewModel(parentEntry)
  ```
- The ViewModel holds the full `MealPlanPreferences` draft state and exposes a single `StateFlow<CreateMealPlanUiState>`.
- Individual screens dispatch typed `UiEvent`s to the ViewModel (e.g. `UpdatePlanLength`, `ToggleDietaryRestriction`). No screen owns a fragment of the state independently.

---

## Consequences

**Positive:**
- Wizard state is automatically cleaned up when the user exits the nested graph — no manual teardown.
- All screens read from the same `StateFlow`; no state synchronisation bugs.
- Easy to unit-test: the ViewModel has no navigation dependency, just a repository.
- Pattern is reusable for any future wizard flows (e.g. onboarding, recipe import).

**Negative / Trade-offs:**
- Requires a nested `NavGraph` declaration. Slightly more boilerplate in `ChefAINavGraph.kt`.
- `navController.getBackStackEntry()` crashes if called when the entry is no longer in the back stack (e.g. after pop). Must guard against this; the current implementation calls it only from composables within the nested graph.

---

## Applicability

Use this pattern whenever:
- 2+ screens need to share mutable state as part of a single user flow.
- The state lifetime should be scoped to that flow, not the whole app.

Do **not** use this pattern for persistent cross-feature state (e.g. current user session) — that belongs in a Singleton-scoped ViewModel or the repository layer.
