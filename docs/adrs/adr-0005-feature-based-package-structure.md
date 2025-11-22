# 🧱 ADR 0005 – Feature-Based Package Structure

**Date:** 2025-11-21  
**Status:** Accepted  
**Context:**  
ChefAI has grown to include multiple distinct features (authentication, recipes, home, meal plans)
and shared infrastructure. The original layer-based package structure (`ui/`, `data/`, `domain/`,
`util/`) made it difficult to identify feature boundaries, complicated parallel development, and
created tight coupling between unrelated features.

---

## Decision

We will organize the codebase into a **feature-based (modular) package structure**, where each
feature encapsulates its own data, domain, and UI layers. Shared code lives in a dedicated `core/`
package.

### Package Structure

```
com.tenmilelabs.chefai/
├── auth/                    # Authentication Feature
│   ├── data/
│   │   ├── local/          # SecurePreferences
│   │   └── network/        # AuthInterceptor
│   ├── domain/
│   │   └── model/          # AuthToken, UserSession
│   ├── ui/                 # UserProfileMenu, AuthState
│   └── util/               # SessionUtils
│
├── recipes/                 # Recipes Feature
│   ├── data/
│   │   ├── mapper/         # RoomDomainMap, NetworkDomainMap
│   │   ├── network/        # API service, network models
│   │   └── repository/     # DefaultRecipeRepository
│   ├── domain/
│   │   └── repository/     # RecipesRepository interface
│   └── ui/
│       ├── create/         # Create recipe screens
│       ├── details/        # Recipe details screens
│       └── [list]          # Recipe list screens
│
├── home/                    # Home Feature
│   └── ui/                 # HomeScreen, HomeViewModel
│
├── mealplans/              # Meal Plans Feature
│   └── ui/                 # MealPlansScreen, MealPlansViewModel
│
├── core/                   # Shared Components
│   ├── data/
│   │   ├── local/room/    # Database (DAOs, Entities, Relations)
│   │   ├── local/util/    # UUID extensions, SyncState
│   │   └── repository/    # Shared repositories
│   ├── di/                # Dependency injection modules
│   ├── domain/
│   │   ├── model/         # Shared domain models (Recipe, User, Tag, Label)
│   │   └── repository/    # Repository interfaces
│   ├── ui/
│   │   ├── components/    # Reusable UI components
│   │   ├── navigation/    # NavGraph, TopAppBar, BottomNav
│   │   ├── preview/       # Compose preview data
│   │   └── theme/         # Material theme
│   └── util/              # Shared utilities
│
├── ChefAIApplication.kt
└── MainActivity.kt
```

### Test Structure

Test packages mirror the main source structure:

```
test/
├── auth/                   # Auth feature tests
│   ├── data/local/        # FakeSecurePreferences
│   └── domain/            # SessionManagerTest
├── recipes/                # Recipes feature tests
│   ├── data/repository/   # Repository tests & fakes
│   └── ui/                # ViewModel tests
├── core/                   # Core tests
│   ├── data/local/room/dao/  # Fake DAOs
│   ├── testutil/          # Shared test data
│   └── util/              # Test utilities
```

---

## Rationale

### 1. Feature Independence

- Each feature is self-contained with its own data, domain, and UI layers
- Features depend on `core`, not on each other
- Clear ownership and boundaries reduce accidental coupling

### 2. Scalability

- New features can be added without modifying existing ones
- Features can be developed independently by different developers/teams
- Easy path to multi-module architecture if needed (`:feature:auth`, `:feature:recipes`, etc.)

### 3. Maintainability

- Easy to find all code related to a specific feature
- Changes to one feature don't inadvertently affect others
- Clearer code review scope (reviews can focus on single features)
- New developers can understand one feature at a time

### 4. Testing

- Tests are co-located with implementations
- Feature-specific fakes and test utilities are clearly organized
- Easy to run tests for a single feature

### 5. Build Performance (Future)

- Enables future modularization into separate Gradle modules
- Parallel compilation and caching per module
- Selective builds (only rebuild changed features)

---

## Guidelines for Adding New Features

### Directory Structure

When adding a new feature, create this structure:

```bash
mkdir -p app/src/main/java/com/tenmilelabs/chefai/[feature]/{data,domain,ui}
mkdir -p app/src/test/java/com/tenmilelabs/chefai/[feature]/{data,domain,ui}
```

### Feature Package Organization

```
feature_name/
├── data/
│   ├── local/              # Local data sources (if needed)
│   ├── network/            # Network data sources (if needed)
│   ├── repository/         # Repository implementations
│   └── mapper/             # Data mappers
├── domain/
│   ├── model/              # Feature-specific domain models (if any)
│   ├── repository/         # Repository interfaces
│   └── usecase/            # Complex business logic (if needed)
└── ui/
    ├── [screen_name]/      # Screen-specific composables
    ├── components/         # Feature-specific UI components
    └── viewmodel/          # ViewModels
```

### Test Package Organization

Mirror the main structure:

```
test/feature_name/
├── data/
│   ├── repository/         # Repository tests, fake repositories
│   └── network/            # Fake API services
├── domain/
│   └── usecase/            # Use case tests (if applicable)
└── ui/
    └── [screen_name]/      # ViewModel tests, UI tests
```

---

## Shared vs Feature Code Decision Tree

### Add to `core/` if the code is:

- ✅ Used by multiple features (e.g., `User`, `Recipe` domain models)
- ✅ Core business domain concept (e.g., `Tag`, `Label`, `Ingredient`)
- ✅ Infrastructure (database, network client, DI modules)
- ✅ Truly reusable UI component (e.g., `InfoChip`, theme)
- ✅ Navigation infrastructure (nav graph, app bar, bottom nav)

### Add to feature package if the code is:

- ✅ Specific to one feature (e.g., `AuthToken`, `SessionManager`)
- ✅ Feature-specific business logic (e.g., recipe validation)
- ✅ Feature-specific UI (e.g., `CreateRecipeScreen`, `RecipeDetailsViewModel`)
- ✅ Feature-specific data handling (e.g., `RecipeMapper`, `RecipeApiService`)

**Rule of thumb:** Start in the feature package. Move to `core/` only when a second feature needs
it.

---

## Naming Conventions

### Packages

- Feature packages: lowercase, singular (e.g., `auth`, `recipe`, not `recipes`)
- Exception: `recipes` kept for historical reasons and semantic clarity
- Layer packages: lowercase (e.g., `data`, `domain`, `ui`)
- Sub-packages: descriptive (e.g., `local`, `network`, `repository`, `components`)

### Classes

- Screens: `[Feature][Purpose]Screen` (e.g., `RecipeDetailsScreen`, `CreateRecipeScreen`)
- ViewModels: `[Feature][Purpose]ViewModel` (e.g., `RecipeDetailsViewModel`)
- Repositories: `[Entity]Repository` (interface), `Default[Entity]Repository` (implementation)
- Mappers: Extension functions in `[Source][Target]Map.kt` (e.g., `RoomDomainMap.kt`)
- Test fakes: `Fake[ClassName]` (e.g., `FakeRecipeRepository`, `FakeRecipeDao`)

---

## Migration Impact

### Changes Made

- **107 main source files** moved to feature packages
- **20 test files** reorganized to mirror main structure
- **62 files** had imports automatically updated
- All package declarations updated
- Empty directories cleaned up

### Build Status

- ✅ Main app: BUILD SUCCESSFUL
- ✅ Unit tests: ALL PASSING
- ✅ No breaking changes to functionality

### Documentation

- Main source reorganization: `FEATURE_REORGANIZATION_SUMMARY.md`
- Test reorganization: `TEST_REORGANIZATION_SUMMARY.md`
- Combined overview: `COMPLETE_REORGANIZATION_SUMMARY.md`

---

## Consequences

### ✅ Benefits

- **Clearer code ownership:** Each feature has clear boundaries and responsibility
- **Reduced coupling:** Features depend on `core`, not each other
- **Better onboarding:** New developers can understand one feature at a time
- **Parallel development:** Multiple features can be worked on simultaneously with fewer conflicts
- **Easier testing:** Tests are co-located; feature boundaries are clear
- **Scalability:** Easy path to multi-module architecture when needed
- **Code reviews:** Smaller, focused reviews per feature

### ⚠️ Trade-offs

- **Slight duplication:** Some utilities might exist in multiple features initially
- **Migration effort:** Existing code required significant reorganization (one-time cost)
- **Discipline required:** Developers must follow guidelines to maintain structure
- **Import path length:** Package paths are longer (e.g.,
  `com.tenmilelabs.chefai.recipes.ui.create`)

### 🔄 Refactoring Considerations

- Moving a class from feature to `core` requires updating multiple imports
- Feature extraction to modules will be easier with this structure
- Adding new features follows a clear, repeatable pattern

---

## Future Evolution

### Phase 1: Current State (Completed)

- ✅ Feature-based package structure within single `:app` module
- ✅ Clear feature boundaries
- ✅ Shared `core` package

### Phase 2: Multi-Module (Future)

When the app grows larger (10+ features, 50+ files per feature), consider:

```
ChefAI/
├── :app                    # Application module
├── :feature:auth          # Auth feature module
├── :feature:recipes       # Recipes feature module
├── :feature:home          # Home feature module
├── :feature:mealplans     # Meal plans feature module
└── :core                  # Core shared module
```

**Benefits:**

- Faster build times (parallel compilation, module-level caching)
- Stricter dependency enforcement (Gradle module dependencies)
- Potential for dynamic feature modules
- Better team scaling (teams own modules)

**Prerequisites:**

- Feature boundaries are stable
- Clear API contracts between modules
- Team size justifies the additional complexity

### Phase 3: Dynamic Features (Future)

For very large apps, consider dynamic feature modules:

- Download features on-demand
- Reduce initial APK size
- Gradual rollout of new features

---

## Examples

### Example: Adding a "Favorites" Feature

1. **Create structure:**

```bash
mkdir -p app/src/main/java/com/tenmilelabs/chefai/favorites/{data/local,domain,ui}
mkdir -p app/src/test/java/com/tenmilelabs/chefai/favorites/{data,ui}
```

2. **Add implementation:**

```kotlin
// favorites/domain/FavoritesRepository.kt
package com.tenmilelabs.chefai.favorites.domain

interface FavoritesRepository {
    fun observeFavorites(): Flow<List<Recipe>>
    suspend fun addFavorite(recipeId: UUID)
    suspend fun removeFavorite(recipeId: UUID)
}

// favorites/data/DefaultFavoritesRepository.kt
package com.tenmilelabs.chefai.favorites.data

class DefaultFavoritesRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    // Dependencies from core
) : FavoritesRepository { /* ... */ }

// favorites/ui/FavoritesScreen.kt
package com.tenmilelabs.chefai.favorites.ui

@Composable
fun FavoritesScreen(/* ... */) { /* ... */ }
```

3. **Add tests:**

```kotlin
// test/favorites/data/FakeFavoritesRepository.kt
package com.tenmilelabs.chefai.favorites.data

class FakeFavoritesRepository : FavoritesRepository { /* ... */ }

// test/favorites/ui/FavoritesViewModelTest.kt
package com.tenmilelabs.chefai.favorites.ui

class FavoritesViewModelTest { /* ... */ }
```

4. **Register in navigation:**

```kotlin
// core/ui/navigation/AppDestinations.kt
enum class AppDestinations {
    // ...
    FAVORITES,
}
```

---

## References

- [Android App Architecture Guide](https://developer.android.com/topic/architecture)
- [Guide to App Modularization](https://developer.android.com/topic/modularization)
- [Package by Feature, Not Layer](https://phauer.com/2020/package-by-feature/)
- [Modular Android Architecture](https://www.youtube.com/watch?v=PZBg5DIzNww) - Philipp Lackner
- ADR-0001: Hybrid Architecture Choice
- ADR-0004: Data Layer Composition

---

## Related Decisions

- **ADR-0001 (Hybrid Architecture):** Feature-based structure aligns with hybrid architecture by
  keeping layers within features
- **ADR-0004 (Data Layer):** Repository pattern works seamlessly within feature packages
- **Future ADR:** Multi-module architecture guidelines when transitioning from single module
