# Backend Prompt: Meal Plans — Persistence & AI Generation

> Generated: March 2026
> Related PR: #118
> Android feature branch: `claude/suspicious-kapitsa`

---

## Context

The Android client has a fully implemented meal planning feature. Users configure a meal plan via a 3-step wizard and the plan is saved locally in Room as `DRAFT` status. The backend needs to:

1. Persist meal plans and their days via the existing push/pull sync protocol
2. Expose an AI generation endpoint that populates a plan's days with matching recipes

The backend stack is **Kotlin + Ktor + PostgreSQL + Exposed DSL + JWT auth**. Follow the same patterns used for recipes/bookmarks sync.

---

## 1. Database Schema

```sql
CREATE TABLE meal_plans (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    status      TEXT        NOT NULL,  -- DRAFT | GENERATING | READY | ARCHIVED
    preferences JSONB       NOT NULL,
    created_at  BIGINT      NOT NULL,
    updated_at  BIGINT      NOT NULL,
    deleted_at  BIGINT      NULL
);

CREATE INDEX idx_meal_plans_user_id    ON meal_plans(user_id);
CREATE INDEX idx_meal_plans_updated_at ON meal_plans(updated_at);

CREATE TABLE meal_plan_days (
    id               UUID    PRIMARY KEY,
    meal_plan_id     UUID    NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    day_index        INT     NOT NULL,  -- 0-based
    dinner_recipe_id UUID    NULL REFERENCES recipes(id),
    lunch_recipe_id  UUID    NULL REFERENCES recipes(id),
    UNIQUE (meal_plan_id, day_index)
);

CREATE INDEX idx_meal_plan_days_meal_plan_id ON meal_plan_days(meal_plan_id);
```

**`preferences` JSONB shape** (mirrors `MealPlanPreferences` on the client):

```json
{
  "planLengthDays": 5,
  "mealType": "DINNER_AND_LUNCH",
  "dietaryRestrictions": ["VEGAN", "GLUTEN_FREE"],
  "recipeSource": "INCLUDE_PUBLIC",
  "maxPrepTimeMinutes": 30,
  "servingsPerMeal": 4,
  "batchCooking": true,
  "leftoverFriendly": false,
  "varietyPreference": "HIGH"
}
```

Enum values:
- `mealType`: `DINNER` | `DINNER_AND_LUNCH`
- `dietaryRestrictions`: `NONE` | `VEGAN` | `VEGETARIAN` | `LOW_CARB` | `GLUTEN_FREE` | `DAIRY_FREE` | `KETO` | `PALEO`
- `recipeSource`: `COLLECTION_ONLY` | `INCLUDE_PUBLIC`
- `varietyPreference`: `HIGH` | `MEDIUM` | `LOW`

---

## 2. Sync Protocol Extensions

Extend the existing `POST /sync/push` and `GET /sync/pull` endpoints — **do not add new endpoints**. Add meal plans alongside the existing `recipes` / `bookmarkedRecipes` fields.

### 2a. Push — Request

Add `mealPlans: List<SyncMealPlanDto>` to `SyncPushRequest`:

```json
{
  "recipes": [...],
  "bookmarkedRecipes": [...],
  "mealPlans": [
    {
      "uuid": "01957a7e-...",
      "name": "My Week Plan",
      "status": "DRAFT",
      "preferencesJson": "{\"planLengthDays\":5,...}",
      "createdAt": 1743000000000,
      "updatedAt": 1743000001000,
      "deletedAt": null,
      "days": [
        {
          "uuid": "01957a7f-...",
          "dayIndex": 0,
          "dinnerRecipeId": null,
          "lunchRecipeId": null
        }
      ]
    }
  ]
}
```

**Push logic (per meal plan):**
- Upsert `meal_plans` row. Conflict resolution: last-writer-wins on `updated_at`.
- Replace all `meal_plan_days` rows for this plan atomically (delete existing, insert new).
- If `deleted_at` is non-null, soft-delete: keep row, set `deleted_at`, do not include in future pulls.
- Return accepted/conflict/error per plan UUID, following the same shape as recipes.

### Push — Response additions

```json
{
  "accepted": [...],
  "conflicts": [...],
  "errors": [...],
  "serverTimestamp": 1743000002000,
  "mealPlans": {
    "accepted": [{"uuid": "01957a7e-...", "serverUpdatedAt": 1743000002000}],
    "conflicts": [],
    "errors": []
  }
}
```

### 2b. Pull — Response

Add `mealPlans: List<SyncMealPlanDto>` to `SyncPullResponse`:

```json
{
  "recipes": [...],
  "serverTimestamp": 1743000002000,
  "hasMore": false,
  "mealPlans": [
    {
      "uuid": "01957a7e-...",
      "name": "My Week Plan",
      "status": "READY",
      "preferencesJson": "{...}",
      "createdAt": 1743000000000,
      "updatedAt": 1743000002000,
      "deletedAt": null,
      "days": [
        {
          "uuid": "01957a7f-...",
          "dayIndex": 0,
          "dinnerRecipeId": "recipe-uuid-1",
          "lunchRecipeId": "recipe-uuid-2"
        }
      ]
    }
  ]
}
```

Pull filter: `WHERE user_id = :userId AND updated_at > :since`. Include soft-deleted plans (`deleted_at IS NOT NULL`) so the client can tombstone them locally. Nest `meal_plan_days` inside each plan in the response — no separate pull for days.

---

## 3. AI Generation Endpoint

```
POST /meal-plans/{mealPlanId}/generate
Authorization: Bearer <jwt>
```

**What it does:**

1. Validate the caller owns this meal plan.
2. Set `status = GENERATING` immediately and return `202 Accepted` with the updated plan (so the client can show a loading state).
3. Asynchronously (coroutine / background job):
   - Query candidate recipes based on preferences (see filtering rules below).
   - Assign recipes to days respecting variety/batch preferences.
   - Upsert `meal_plan_days` rows.
   - Set `status = READY`, update `updated_at`.
   - The client will pick up the update on next pull.

**Response (202):**
```json
{
  "uuid": "01957a7e-...",
  "status": "GENERATING",
  "updatedAt": 1743000005000
}
```

**Recipe candidate filtering rules** (from `preferences`):

| Preference | Filter |
|---|---|
| `recipeSource = COLLECTION_ONLY` | `recipe_id IN (SELECT recipe_id FROM bookmarked_recipes WHERE user_id = :userId AND deleted_at IS NULL)` |
| `recipeSource = INCLUDE_PUBLIC` | `privacy = 'PUBLIC'` OR bookmarked |
| `dietaryRestrictions` (non-NONE) | recipes must be tagged with ALL selected restriction tags |
| `maxPrepTimeMinutes` non-null | `prep_time_minutes + cook_time_minutes <= maxPrepTimeMinutes` |
| `mealType = DINNER` | only populate `dinner_recipe_id`; leave `lunch_recipe_id = null` |
| `mealType = DINNER_AND_LUNCH` | populate both slots per day |

**Assignment rules:**
- `varietyPreference = HIGH` → no repeat recipes across the plan
- `varietyPreference = MEDIUM` → recipes may repeat after 3 days gap
- `varietyPreference = LOW` → free repetition (pick highest-rated / most bookmarked)
- `batchCooking = true` → prefer recipes that appear in consecutive days (intentional repeats for batch prep)
- `servingsPerMeal` — pass through to the response; no filtering needed server-side (client uses it for shopping list generation later)
- If not enough candidates exist to fill all slots: fill what's possible, set `status = READY` with partial days (nulls are valid).

**Error response (404 / 403):**
```json
{"error": "NOT_FOUND", "message": "Meal plan not found or not owned by caller"}
```

---

## 4. Client-side sync wiring needed (Android, follow-up PR)

The Android client needs these additions to complete the integration — not in scope for this backend task but listed here so both sides are aligned:

- Add `SyncMealPlanDto` / `SyncMealPlanDayDto` to `SyncDtos.kt`
- Extend `SyncPushRequest` with `mealPlans`
- Extend `SyncPullResponse` with `mealPlans`
- Add meal plan push/pull logic to `SyncOrchestrator`
- After a plan transitions to `GENERATING` or `READY` on pull, update local Room status and trigger UI update via the existing `MealPlansViewModel` flow

---

## 5. Definition of Done

- [ ] Migration script creating `meal_plans` and `meal_plan_days` tables
- [ ] `POST /sync/push` accepts and persists `mealPlans` array
- [ ] `GET /sync/pull` returns `mealPlans` for the authenticated user filtered by `since`
- [ ] `POST /meal-plans/{id}/generate` returns `202`, sets status to `GENERATING`, populates days async, then sets `READY`
- [ ] Soft-deletes propagate correctly through push and appear in pull with `deletedAt` set
- [ ] Unit tests for the generation candidate-filtering and assignment logic
- [ ] Integration test: push DRAFT plan → call generate → pull → verify days populated
