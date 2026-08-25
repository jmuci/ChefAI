# Meal-plan shopping list — implementation plan

**Goal.** From an open meal plan, a shopping-cart FAB opens a new screen listing every ingredient
the plan needs, with quantities, grouped by grocery-store section, alphabetical inside each section.
Each row has a checkbox; ticking it animates the row to a dimmed, struck-through state. Ticks are
persisted per plan.

Written to be implemented end-to-end by Sonnet 5. Every decision below is already made — where a
trade-off existed, the chosen side and its reason are stated so you don't re-litigate it.

---

## 0. Decisions already made (do not change without asking)

| Question | Decision | Why |
|---|---|---|
| Where does the list come from? | Derived on the fly from `meal_plans` → `meal_plan_days` → `recipe_ingredients`. **No** `shopping_list_items` table. | The plan is the source of truth; a materialised list would drift the moment a plan is regenerated. |
| What identifies a line item? | **Normalised ingredient display name** (`trim().lowercase()`, internal whitespace collapsed) — *not* `ingredients.uuid`. | The catalog can hold two `ingredients` rows named "Olive oil" (the editor dedupes by name, but scraped imports and sidecar imports mint fresh UUIDs). Grouping by name is what a shopper expects. |
| Are ticks persisted? | Yes — a new local-only Room table `shopping_list_checks`, keyed `(mealPlanId, itemKey)`. DB **v6 → v7**. | You tick items while standing in a shop; losing them on back-navigation or process death is unacceptable. Keyed on the name, so it survives `SyncOrchestrator.applyPulledMealPlan` regenerating `meal_plan_days` UUIDs. |
| Does tick state sync? | **No.** Local-only, exactly like the cooked timestamps. No `syncState`/`updatedAt`, not a `SyncableEntity`, no `requestMutationSync()`. | The backend payload has no field for it, and a shopping tick is device-local by nature. |
| Are cooked meals excluded? | **No** — the list covers the whole plan. | Items must not vanish from under you mid-shop. A "hide meals already cooked" filter is a follow-up. |
| Are quantities scaled to the plan's servings? | **Yes**, `plannedServings / recipeServings`, guarded against zero. | The wizard asks for servings-per-meal; ignoring it would make the numbers wrong. Isolated in one function so it's easy to remove. |
| Are units converted (tsp → tbsp, g → kg)? | **No.** Same-unit quantities are summed; different units are listed side by side (`"2 tbsp + 100 ml"`). | Unit conversion needs a real unit model (`recipe_ingredients.unit` is a free-text `String` with a `TODO Make enum`). Out of scope. |
| How is a section assigned? | Keyword match on the ingredient's display name, pure Kotlin, no DB column. | `ingredients.sourcePrimaryId` is `null` for everything created locally (`SidecarMapper.kt:68`), so it's not a usable signal. `IngredientEntity`'s `// TODO add grocery section ?` stays a TODO — adding a column would mean a sync-payload field and a backend change. |
| Do ticked rows move to the bottom? | **No** — they stay in alphabetical position. | Rows jumping under your thumb while shopping is worse than a tidy list. |

---

## 1. File-by-file work list

New files are marked ✨; the rest are edits.

### Data — persistence

1. ✨ `app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/ShoppingListCheckEntity.kt`
2. ✨ `app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/dao/ShoppingListCheckDao.kt`
3. ✨ `app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/relations/PlanIngredientRow.kt`
4. `core/data/local/room/dao/ChefAIDataBase.kt` — register entity + DAO, bump `version = 7`, add `MIGRATION_6_7`
5. `core/data/local/room/dao/RecipeDao.kt` — add `observeIngredientsForRecipes(...)`
6. `core/di/DataModules.kt` — register `MIGRATION_6_7`, provide the DAO, bind the repository

### Domain — pure Kotlin, no Android imports

7. ✨ `mealplans/domain/shoppinglist/GrocerySection.kt`
8. ✨ `mealplans/domain/shoppinglist/GrocerySectionClassifier.kt`
9. ✨ `mealplans/domain/shoppinglist/PlannedIngredient.kt`
10. ✨ `mealplans/domain/shoppinglist/ShoppingList.kt` (models + `ShoppingListBuilder`)
11. ✨ `mealplans/domain/repository/ShoppingListRepository.kt`

### Data — repository

12. ✨ `mealplans/data/repository/DefaultShoppingListRepository.kt`

### UI

13. ✨ `mealplans/ui/shoppinglist/ShoppingListViewModel.kt`
14. ✨ `mealplans/ui/shoppinglist/ShoppingListScreen.kt`
15. ✨ `mealplans/ui/shoppinglist/components/ShoppingListRow.kt`

### Navigation

16. `core/ui/navigation/AppDestinations.kt` — base route, destination, `navigateToMealPlanShoppingList`
17. `core/ui/navigation/ChefAINavGraph.kt` — `composable(...)` + the FAB branch
18. `core/ui/navigation/BottomNavigationBar.kt` — add the base route to `MEAL_PLANS.childRoutePrefixes`

### Resources

19. `app/src/main/res/values/strings.xml`

### Tests

20. ✨ `test/.../mealplans/domain/shoppinglist/GrocerySectionClassifierTest.kt`
21. ✨ `test/.../mealplans/domain/shoppinglist/ShoppingListBuilderTest.kt`
22. ✨ `test/.../mealplans/ui/shoppinglist/ShoppingListViewModelTest.kt`
23. ✨ `test/.../mealplans/data/repository/FakeShoppingListRepository.kt`
24. ✨ `test/.../core/data/local/room/dao/FakeShoppingListCheckDao.kt`
25. `test/.../core/data/local/room/dao/FakeRecipeDao.kt` — implement the new method
26. `test/.../core/ui/navigation/BottomNavigationBarTest.kt` — cover the new route
27. `androidTest/.../data/source/local/ChefAIDatabaseMigrationTest.kt` — `migrate6To7…` + extend `migrateAll1To6` → `1To7`

---

## 2. Data layer

### 2.1 `ShoppingListCheckEntity`

```kotlin
package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

/**
 * One ticked-off line on a meal plan's shopping list.
 *
 * A row's presence *is* the tick; unticking deletes it. Keyed on the normalised ingredient name
 * (see `ShoppingListBuilder.nameKey`) rather than on `ingredients.uuid`, because the catalog can
 * hold several rows for the same ingredient — and rather than on a day/slot, because
 * [com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator] regenerates `meal_plan_days` UUIDs
 * whenever a pull replaces a plan's days.
 *
 * Local-only, like the cooked timestamps on [MealPlanDayEntity]: the sync payload has no field for
 * it, so this deliberately carries no `syncState`/`updatedAt` and does not implement
 * [com.tenmilelabs.chefai.core.data.local.util.SyncableEntity].
 */
@Entity(
    tableName = "shopping_list_checks",
    primaryKeys = ["mealPlanId", "itemKey"],
    foreignKeys = [
        ForeignKey(
            entity = MealPlanEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["mealPlanId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("mealPlanId")],
)
data class ShoppingListCheckEntity(
    val mealPlanId: UUID,
    val itemKey: String,
    /** Epoch millis the item was ticked. Not shown anywhere yet; kept for a future "recently bought". */
    val checkedAt: Long,
)
```

### 2.2 `ShoppingListCheckDao`

```kotlin
@Dao
interface ShoppingListCheckDao {
    @Query("SELECT itemKey FROM shopping_list_checks WHERE mealPlanId = :mealPlanId")
    fun observeCheckedKeys(mealPlanId: UUID): Flow<List<String>>

    @Upsert
    suspend fun upsert(check: ShoppingListCheckEntity)

    @Query("DELETE FROM shopping_list_checks WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey")
    suspend fun delete(mealPlanId: UUID, itemKey: String)

    @Query("DELETE FROM shopping_list_checks WHERE mealPlanId = :mealPlanId")
    suspend fun clearForPlan(mealPlanId: UUID)
}
```

### 2.3 `ChefAIDataBase`

- Add `ShoppingListCheckEntity::class` to `entities`, `version = 7`.
- Add `abstract fun shoppingListCheckDao(): ShoppingListCheckDao`.
- Append at the bottom of the file, next to `MIGRATION_5_6`:

```kotlin
/**
 * Adds `shopping_list_checks`, which backs ticking items off a meal plan's shopping list.
 *
 * Local-only, so there is no sync bookkeeping on it. `ON DELETE CASCADE` from `meal_plans` means a
 * deleted plan takes its ticks with it — the list has no meaning without the plan.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_list_checks` (
                `mealPlanId` BLOB NOT NULL, `itemKey` TEXT NOT NULL, `checkedAt` INTEGER NOT NULL,
                PRIMARY KEY(`mealPlanId`, `itemKey`),
                FOREIGN KEY(`mealPlanId`) REFERENCES `meal_plans`(`uuid`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_list_checks_mealPlanId` " +
                "ON `shopping_list_checks` (`mealPlanId`)"
        )
    }
}
```

> **Verify the DDL, don't trust it.** `exportSchema = true`, so after the first compile read
> `app/schemas/com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase/7.json` and make the
> `CREATE TABLE`/`CREATE INDEX` strings byte-identical to what Room generated.
> `runMigrationsAndValidate` compares column order, types, nullability, indices and foreign keys,
> and will fail on any difference.

### 2.4 `RecipeDao.observeIngredientsForRecipes`

New projection, alongside the existing `RecipeIngredient`:

```kotlin
package com.tenmilelabs.chefai.core.data.local.room.relations

import java.util.UUID

/**
 * One ingredient of one recipe, with the recipe's own serving count so a shopping list can scale
 * the quantity to the servings a meal plan asked for.
 */
data class PlanIngredientRow(
    val recipeId: UUID,
    val recipeServings: Int,
    val ingredientId: UUID,
    val ingredientDisplayName: String,
    val quantity: Double,
    val unit: String,
)
```

Added to `RecipeDao` right below `observeIngredientsForRecipe`:

```kotlin
/**
 * Ingredients for a batch of recipes in one query, for the meal-plan shopping list.
 *
 * Unlike [observeIngredientsForRecipe] this filters soft-deleted rows out on all three tables: a
 * shopping list must not send you out for something belonging to a deleted recipe.
 */
@Query("""
    SELECT
        ri.recipeId AS recipeId,
        r.servings AS recipeServings,
        ri.ingredientId AS ingredientId,
        i.displayName AS ingredientDisplayName,
        ri.quantity AS quantity,
        ri.unit AS unit
    FROM recipe_ingredients AS ri
    INNER JOIN recipes AS r ON ri.recipeId = r.uuid
    INNER JOIN ingredients AS i ON ri.ingredientId = i.uuid
    WHERE ri.recipeId IN (:recipeIds)
      AND ri.deletedAt IS NULL
      AND r.deletedAt IS NULL
      AND i.deletedAt IS NULL
""")
fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlanIngredientRow>>
```

### 2.5 `DataModules.kt`

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

```kotlin
@Provides
fun provideShoppingListCheckDao(database: ChefAIDataBase) = database.shoppingListCheckDao()
```

and in the `@Binds` module, next to `bindMealPlanRepository`:

```kotlin
@Singleton
@Binds
abstract fun bindShoppingListRepository(repository: DefaultShoppingListRepository): ShoppingListRepository
```

---

## 3. Domain layer (pure Kotlin — no `android.*`, no `androidx.*`, no `R`)

### 3.1 `GrocerySection`

Declaration order **is** display order — roughly the walk through a supermarket, `OTHER` last.
Labels are plain strings, matching `MealSlot`/`MealType` in this feature (not `@StringRes`, which
would drag an Android dependency into the domain layer).

```kotlin
package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

/** A supermarket aisle, in the order you walk them. Declaration order is display order. */
enum class GrocerySection(val label: String, val emoji: String) {
    PRODUCE("Fresh produce", "🥬"),
    BAKERY("Bakery", "🥖"),
    MEAT_AND_SEAFOOD("Meat & seafood", "🥩"),
    DAIRY_AND_EGGS("Dairy & eggs", "🥛"),
    FROZEN("Frozen", "🧊"),
    PANTRY("Pantry & dry goods", "🥫"),
    SPICES_AND_BAKING("Spices & baking", "🧂"),
    BEVERAGES("Drinks", "🧃"),
    OTHER("Other", "🛒"),
}
```

### 3.2 `GrocerySectionClassifier`

**Matching rules — implement exactly:**

1. Normalise: lowercase, replace every non-alphanumeric character with a space, collapse runs of
   whitespace, trim. `"Extra-virgin Olive Oil (cold pressed)"` → `"extra virgin olive oil cold pressed"`.
2. **Overrides first**, in this order, first hit wins:
   - contains `"frozen"` or `"ice cream"` → `FROZEN`
   - contains `"canned"`, `"tinned"`, `"jarred"` or `"tin of"` → `PANTRY`
3. **Phrase rules** (multi-word keywords) across every section, in the section priority order given
   below, matched with `normalized.contains(phrase)`.
4. **Token rules** (single-word keywords) in the same priority order. A token keyword `k` matches
   when some token `t` of the normalised name satisfies `t == k || t.removeSuffix("s") == k`.
   *Whole-token matching is load-bearing:* it is why `"eggplant"` is produce and not eggs, and why
   `"cornstarch"` does not match the token `corn`.
5. No match → `OTHER`.

Phrases are checked before tokens **globally**, which is what routes `"coconut milk"` to `PANTRY`
even though `milk` is a `DAIRY_AND_EGGS` token.

**Priority orders** (both differ from display order):

- **Phrases:** `PANTRY`, `SPICES_AND_BAKING`, `DAIRY_AND_EGGS`, `MEAT_AND_SEAFOOD`, `BAKERY`,
  `BEVERAGES`, `PRODUCE`.
  `PANTRY` leads here — and only here — so `"chicken stock"` resolves before the `MEAT_AND_SEAFOOD`
  token `chicken` can send stock to the meat counter. That is safe because every `PANTRY` phrase is
  a specific compound ("olive oil", "coconut milk"), never a bare generic.
- **Tokens:** `SPICES_AND_BAKING`, `DAIRY_AND_EGGS`, `MEAT_AND_SEAFOOD`, `BAKERY`, `BEVERAGES`,
  `PRODUCE`, `PANTRY`.
  `SPICES_AND_BAKING` leads so "cinnamon" and "yeast" don't get swallowed by `PANTRY`, and `PANTRY`
  trails as the catch-all before `OTHER`.

**Keyword table** — start from this; it is deliberately mid-sized rather than exhaustive, and
`OTHER` is a perfectly good answer for anything unlisted.

```
SPICES_AND_BAKING
  phrases: baking powder, baking soda, vanilla extract, brown sugar, icing sugar,
           powdered sugar, food colouring, food coloring, cocoa powder, curry powder,
           chilli powder, chili powder, bay leaf, bay leaves
  tokens:  salt, pepper, peppercorn, cinnamon, nutmeg, paprika, cumin, coriander, turmeric,
           oregano, thyme, rosemary, sage, chilli, chili, cayenne, clove, cardamom, saffron,
           sugar, flour, yeast, cocoa, vanilla, gelatin, gelatine, cornstarch, cornflour,
           breadcrumb, breadcrumbs, seasoning, spice, spices, extract
DAIRY_AND_EGGS
  phrases: almond milk, oat milk, soy milk, soya milk, sour cream, cream cheese,
           double cream, single cream, heavy cream, whipping cream, creme fraiche,
           cottage cheese, greek yogurt, greek yoghurt, condensed milk, evaporated milk
  tokens:  milk, butter, cheese, yogurt, yoghurt, cream, egg, eggs, mozzarella, parmesan,
           cheddar, feta, ricotta, mascarpone, halloumi, gouda, brie, ghee, custard, kefir
MEAT_AND_SEAFOOD
  phrases: chicken breast, chicken thigh, chicken thighs, minced beef, ground beef,
           minced pork, ground pork, pork belly, streaky bacon, smoked salmon
  tokens:  chicken, beef, pork, lamb, veal, turkey, duck, bacon, ham, sausage, salami,
           chorizo, prosciutto, pancetta, mince, steak, brisket, ribs, fish, salmon, tuna,
           cod, haddock, tilapia, trout, sardine, anchovy, anchovies, prawn, prawns, shrimp,
           crab, lobster, mussel, mussels, clam, clams, squid, calamari, scallop, scallops
BAKERY
  phrases: bread rolls, burger buns, hot dog buns, pita bread, naan bread, puff pastry,
           filo pastry, phyllo pastry, shortcrust pastry, sourdough loaf, tortilla wraps
  tokens:  bread, baguette, ciabatta, sourdough, brioche, bun, buns, roll, rolls, bagel,
           croissant, tortilla, pita, naan, focaccia, crumpet, muffin, pastry, loaf
BEVERAGES
  phrases: orange juice, apple juice, lemon juice, sparkling water, coconut water
  tokens:  juice, water, coffee, tea, wine, beer, cider, soda, lemonade, cola, kombucha
PRODUCE
  phrases: spring onion, spring onions, green onion, green onions, bell pepper, bell peppers,
           sweet potato, sweet potatoes, cherry tomato, cherry tomatoes, baby spinach,
           romaine lettuce, butternut squash, fresh parsley, fresh basil, fresh coriander,
           fresh cilantro, fresh mint, fresh dill, fresh ginger, salad leaves, mixed salad
  tokens:  onion, garlic, shallot, leek, tomato, potato, carrot, celery, cucumber, courgette,
           zucchini, aubergine, eggplant, pepper, capsicum, mushroom, broccoli, cauliflower,
           cabbage, kale, spinach, lettuce, rocket, arugula, chard, asparagus, pea, peas,
           bean, beans, corn, pumpkin, squash, radish, beetroot, beet, turnip, parsnip,
           fennel, ginger, avocado, lemon, lime, orange, apple, banana, pear, grape, grapes,
           berry, berries, strawberry, blueberry, raspberry, blackberry, mango, pineapple,
           peach, plum, cherry, melon, watermelon, kiwi, coconut, date, dates, fig, figs,
           parsley, basil, cilantro, mint, dill, chive, chives, scallion, sprout, sprouts,
           herb, herbs, salad
PANTRY
  phrases: chicken stock, vegetable stock, beef stock, chicken broth, vegetable broth,
           olive oil, sunflower oil, vegetable oil, sesame oil, coconut oil, rapeseed oil,
           canola oil, soy sauce, soya sauce, fish sauce, tomato paste, tomato puree,
           tomato passata, chopped tomatoes, peanut butter, maple syrup, coconut milk,
           coconut cream, stock cube, stock cubes, bouillon cube, chickpea flour,
           dijon mustard, worcestershire sauce, balsamic vinegar, apple cider vinegar
  tokens:  oil, vinegar, rice, pasta, spaghetti, penne, macaroni, linguine, tagliatelle,
           fusilli, lasagne, lasagna, noodle, noodles, couscous, quinoa, bulgur, barley,
           lentil, lentils, chickpea, chickpeas, oat, oats, cereal, granola, honey, syrup,
           jam, marmalade, ketchup, mustard, mayonnaise, mayo, sauce, stock, broth, bouillon,
           tahini, hummus, pesto, salsa, tofu, tempeh, seitan, nut, nuts, almond, walnut,
           cashew, pecan, pistachio, peanut, hazelnut, seed, seeds, raisin, raisins, sultana,
           cracker, crackers, crisps, chips, chocolate, molasses, cornmeal, polenta, semolina
```

Shape:

```kotlin
object GrocerySectionClassifier {

    fun classify(ingredientName: String): GrocerySection { … }

    /** Lowercased, punctuation replaced by spaces, whitespace collapsed. */
    internal fun normalize(raw: String): String { … }

    private val phraseRules: List<Pair<GrocerySection, List<String>>> = …
    private val tokenRules: List<Pair<GrocerySection, Set<String>>> = …
}
```

Keep the tables as `private val` top-level constants inside the object so they are built once.

### 3.3 `PlannedIngredient`

Domain mirror of `PlanIngredientRow`, so the repository interface doesn't leak a Room relation:

```kotlin
package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import java.util.UUID

/** One ingredient of one planned recipe, as the shopping list needs it. */
data class PlannedIngredient(
    val recipeId: UUID,
    /** Servings the recipe itself yields; `0` when unknown, which disables scaling for this row. */
    val recipeServings: Int,
    val displayName: String,
    val quantity: Double,
    val unit: String,
)
```

### 3.4 `ShoppingList.kt` — models + builder

```kotlin
/** One line on the list: an ingredient, how much of it, and whether it's been picked up. */
data class ShoppingListItem(
    /** Normalised name; the stable key for the tick row and for `LazyColumn` item keys. */
    val key: String,
    val displayName: String,
    /** e.g. "500 g", "2 tbsp + 100 ml", or `null` when no usable quantity was recorded. */
    val quantityLabel: String?,
    val section: GrocerySection,
    val isChecked: Boolean,
)

/** One aisle's worth of items, alphabetical. */
data class ShoppingListSection(
    val section: GrocerySection,
    val items: List<ShoppingListItem>,
) {
    val checkedCount: Int get() = items.count { it.isChecked }
}

data class ShoppingList(val sections: List<ShoppingListSection>) {
    val totalCount: Int get() = sections.sumOf { it.items.size }
    val checkedCount: Int get() = sections.sumOf { it.checkedCount }
    val isEmpty: Boolean get() = totalCount == 0
    /** Ticked share in `0f..1f`; `0f` for an empty list. */
    val progress: Float get() = if (totalCount == 0) 0f else checkedCount.toFloat() / totalCount
}
```

```kotlin
/**
 * Turns a plan's recipe ingredients into a grouped, aggregated shopping list.
 *
 * Pure and Android-free so the aggregation rules can be unit-tested directly; the ViewModel only
 * wraps the result in its UI state. Mirrors how [MealPlanBoard] relates to `MealPlanDetailViewModel`.
 */
object ShoppingListBuilder {

    /**
     * @param ingredients every ingredient row for every distinct recipe in the plan.
     * @param slotCountByRecipe how many slots each recipe fills in the plan. A recipe cooked twice
     *   in a week needs twice the shopping, and [ingredients] carries its rows only once.
     * @param plannedServings the plan's servings-per-meal; `0` or less disables scaling.
     * @param checkedKeys item keys already ticked off, from `shopping_list_checks`.
     */
    fun build(
        ingredients: List<PlannedIngredient>,
        slotCountByRecipe: Map<UUID, Int>,
        plannedServings: Int,
        checkedKeys: Set<String>,
    ): ShoppingList

    /** Lowercased, trimmed, internal whitespace collapsed. The tick key and the grouping key. */
    fun nameKey(displayName: String): String =
        displayName.trim().lowercase().replace(WHITESPACE, " ")
}
```

**Algorithm, step by step:**

1. Drop rows whose `displayName.isBlank()`.
2. Per row, compute the scaled amount:
   ```kotlin
   val servingsFactor =
       if (plannedServings > 0 && row.recipeServings > 0) plannedServings.toDouble() / row.recipeServings
       else 1.0
   val slots = slotCountByRecipe[row.recipeId] ?: 1
   val amount = row.quantity * servingsFactor * slots
   ```
3. Group rows by `nameKey(displayName)`.
4. Per group:
   - `displayName` = the most frequent original spelling, ties broken by natural string order —
     deterministic, so tests don't flake:
     ```kotlin
     val displayName = rows.groupingBy { it.displayName }.eachCount().entries
         .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
         .first().key
     ```
   - Sub-group by `unitKey = unit.trim().lowercase()`, sum `amount` in each.
   - `quantityLabel`: for each sub-group with a sum `> 0`, render `formatQuantity(sum)` plus a space
     and the unit's original (first-seen, trimmed) spelling when the unit is non-blank. Order
     sub-groups by `unitKey` ascending, with the blank unit **last**. Join with `" + "`. If nothing
     survives, `quantityLabel = null`.
   - `section` = `GrocerySectionClassifier.classify(displayName)`.
   - `isChecked` = `key in checkedKeys`.
5. Group items by `section`; emit sections in `GrocerySection` declaration order, dropping empty
   ones; sort items inside a section with
   `compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }`.

**`formatQuantity`** — put it in the same file, `internal`, and unit-test it:

```kotlin
/** "2", "0.5", "1.25" — at most two decimals, no trailing zeros, no scientific notation. */
internal fun formatQuantity(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    return if (rounded == floor(rounded) && abs(rounded) < 1e15) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
```

---

## 4. Repository

```kotlin
package com.tenmilelabs.chefai.mealplans.domain.repository

interface ShoppingListRepository {
    /** Ingredient rows for the given recipes; empty flow for an empty id list. */
    fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>>

    /** Item keys ticked off on this plan. */
    fun observeCheckedItems(mealPlanId: UUID): Flow<Set<String>>

    suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean)

    suspend fun clearChecks(mealPlanId: UUID)
}
```

```kotlin
@Singleton
class DefaultShoppingListRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val shoppingListCheckDao: ShoppingListCheckDao,
) : ShoppingListRepository {

    override fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>> {
        if (recipeIds.isEmpty()) return flowOf(emptyList())
        return recipeDao.observeIngredientsForRecipes(recipeIds).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeCheckedItems(mealPlanId: UUID): Flow<Set<String>> =
        shoppingListCheckDao.observeCheckedKeys(mealPlanId).map { it.toSet() }

    override suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean) {
        if (checked) {
            shoppingListCheckDao.upsert(
                ShoppingListCheckEntity(mealPlanId, itemKey, System.currentTimeMillis())
            )
        } else {
            shoppingListCheckDao.delete(mealPlanId, itemKey)
        }
    }

    override suspend fun clearChecks(mealPlanId: UUID) = shoppingListCheckDao.clearForPlan(mealPlanId)
}

private fun PlanIngredientRow.toDomain() = PlannedIngredient(
    recipeId = recipeId,
    recipeServings = recipeServings,
    displayName = ingredientDisplayName,
    quantity = quantity,
    unit = unit,
)
```

Put the mapper in `mealplans/data/mapper/ShoppingListMapper.kt` if you prefer to match the feature's
existing `data/mapper/` convention; a private file-level function in the repository is also fine.
Deliberately **no** `syncScheduler.requestMutationSync()` anywhere in here.

---

## 5. ViewModel

`mealplans/ui/shoppinglist/ShoppingListViewModel.kt` — mirror `MealPlanDetailViewModel` closely
(same `SavedStateHandle` arg extraction, same `_events` pattern, same `stateIn` configuration).

```kotlin
sealed interface ShoppingListUiState {
    data object Loading : ShoppingListUiState
    data object NotFound : ShoppingListUiState
    data class Success(
        val planName: String,
        val list: ShoppingList,
    ) : ShoppingListUiState
}

sealed interface ShoppingListEvent {
    data class ShowError(val message: String) : ShoppingListEvent
}
```

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealPlanRepository: MealPlanRepository,
    private val shoppingListRepository: ShoppingListRepository,
) : ViewModel() {

    private val mealPlanId: UUID = UUID.fromString(
        savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_ID_ARG)
            ?: error("Missing mealPlanId argument")
    )

    private val _events = MutableSharedFlow<ShoppingListEvent>()
    val events: SharedFlow<ShoppingListEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ShoppingListUiState> = mealPlanRepository.observeMealPlan(mealPlanId)
        .flatMapLatest { plan ->
            if (plan == null) {
                flowOf(ShoppingListUiState.NotFound)
            } else {
                // A recipe filling two slots needs two shops' worth, so count slots rather than
                // deduplicating to a plain id list.
                val slotCounts: Map<UUID, Int> = plan.days
                    .flatMap { day -> MealSlot.entries.mapNotNull { day.recipeIdFor(it) } }
                    .groupingBy { it }
                    .eachCount()

                combine(
                    shoppingListRepository.observeIngredientsForRecipes(slotCounts.keys.toList()),
                    shoppingListRepository.observeCheckedItems(mealPlanId),
                ) { ingredients, checked ->
                    ShoppingListUiState.Success(
                        planName = plan.name,
                        list = ShoppingListBuilder.build(
                            ingredients = ingredients,
                            slotCountByRecipe = slotCounts,
                            plannedServings = plan.preferences.servingsPerMeal,
                            checkedKeys = checked,
                        ),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingListUiState.Loading)

    fun onToggleItem(item: ShoppingListItem) { … }   // setChecked(mealPlanId, item.key, !item.isChecked)
    fun onUncheckAll() { … }                          // clearChecks(mealPlanId)
}
```

Both actions: `viewModelScope.launch { try { … } catch (e: Exception) { if (e is CancellationException) throw e; Timber.e(…); _events.emit(ShowError(…)) } }` — copy the shape of
`MealPlanDetailViewModel.onToggleCooked`.

---

## 6. UI

### 6.1 `ShoppingListScreen.kt`

Stateless content function + a thin `hiltViewModel()` wrapper, exactly like `MealPlanDetailScreen`.

```kotlin
@Composable
fun ShoppingListScreen(
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    viewModel: ShoppingListViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
)
```

`LaunchedEffect(Unit) { viewModel.events.collect { … showSnackbar } }`, then a `when` over the state:

- `Loading` → `LoadingContent(modifier)` (`core/util`)
- `NotFound` → `EmptyContent(R.string.meal_plan_not_found, R.string.meal_plan_not_found_subtitle, R.drawable.ic_skillet_cooktop_24dp, modifier)`
- `Success` with `list.isEmpty` → `EmptyContent(R.string.shopping_list_empty_title, R.string.shopping_list_empty_subtitle, R.drawable.ic_skillet_cooktop_24dp, modifier)`
- `Success` otherwise → `ShoppingListContent(...)`

`ShoppingListContent(state, onToggleItem, onUncheckAll, modifier)`:

```
Column(Modifier.fillMaxSize())
├─ ShoppingListHeader                     // Card, primaryContainer, same shape as ProgressHeader
│    ├─ Row: plan name (titleLarge, Bold, weight 1f)
│    │       + TextButton "Uncheck all" (only when list.checkedCount > 0)
│    ├─ LinearProgressIndicator(progress = { animatedProgress }, height 8.dp,
│    │       gapSize = 0.dp, drawStopIndicator = {})
│    └─ Text: "8 of 24 picked up"  (plurals-free string with two %d)
└─ LazyColumn(contentPadding = PaddingValues(start=16,end=16,top=8,bottom=88))
     for each section in state.list.sections:
       stickyHeader(key = "header-${section.section.name}") { SectionHeader(section) }
       items(section.items, key = { "item-${it.key}" }) { item ->
           ShoppingListRow(
               name = item.displayName,
               quantityLabel = item.quantityLabel,
               isChecked = item.isChecked,
               onToggle = { onToggleItem(item) },
           )
       }
```

Notes:
- `bottom = 88.dp` on the LazyColumn keeps the last row clear of the FAB area / bottom nav.
- Animate the header progress with `animateFloatAsState(state.list.progress, label = "shoppingProgress")`,
  same as `ProgressHeader`.
- `SectionHeader`: `"${section.emoji}  ${section.label}"` in `titleSmall`/`SemiBold`, `onSurfaceVariant`,
  with `"${checkedCount}/${items.size}"` right-aligned. Give it a solid
  `MaterialTheme.colorScheme.surface` background so scrolling content doesn't show through the
  sticky header, plus `padding(vertical = 8.dp)`.
- `stickyHeader` should be stable on Compose BOM `2025.12.01`. If the compiler still demands it, add
  `@OptIn(ExperimentalFoundationApi::class)` on the composable rather than dropping to a plain `item`.
- **Previews required** (project rule): light + dark for a populated list, plus one empty-list
  preview. Build the preview state from hand-written `ShoppingList` literals — do not call the
  builder from a `@Preview`.

### 6.2 `ShoppingListRow.kt` — the animated tick

The animation is a left-to-right sweeping strike-through plus a fade to a dimmed colour, both over
`240ms` with `FastOutSlowInEasing`.

```kotlin
package com.tenmilelabs.chefai.mealplans.ui.shoppinglist.components

/** How much of its normal opacity a picked-up row keeps. Matches MealPlanMealRow's COOKED_ALPHA. */
private const val CHECKED_ALPHA = 0.45f
private const val STRIKE_ANIM_MS = 240

/**
 * One line of the shopping list: checkbox, ingredient, quantity.
 *
 * Ticking sweeps a strike-through across the text left-to-right and fades the row back, rather than
 * snapping to `TextDecoration.LineThrough` — the sweep is what makes a tick feel like crossing
 * something off a paper list.
 */
@Composable
fun ShoppingListRow(
    name: String,
    quantityLabel: String?,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held as State, not unwrapped with `by`: nothing in the composition reads .value, so each
    // animation frame redraws without recomposing the row.
    val strike = animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = tween(STRIKE_ANIM_MS, easing = FastOutSlowInEasing),
        label = "strikeThrough",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(STRIKE_ANIM_MS),
        label = "rowContentColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = isChecked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .semantics {
                stateDescription = if (isChecked) "Picked up" else "Still to buy"
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // onCheckedChange = null: the whole row owns the click via toggleable, so the box must not
        // register a second, competing semantics node.
        Checkbox(checked = isChecked, onCheckedChange = null)

        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = lerp(1f, CHECKED_ALPHA, strike.value) },
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sweepingStrikeThrough(contentColor) { strike.value },
            )
            if (quantityLabel != null) {
                Text(
                    text = quantityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Draws a line through the content, sweeping left→right as [progress] runs 0→1. */
private fun Modifier.sweepingStrikeThrough(
    color: Color,
    progress: () -> Float,
): Modifier = drawWithContent {
    drawContent()
    val fraction = progress()
    if (fraction > 0f) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width * fraction, y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
```

Imports worth naming up front: `androidx.compose.foundation.selection.toggleable`,
`androidx.compose.ui.draw.drawWithContent`, `androidx.compose.ui.graphics.drawscope.*`,
`androidx.compose.ui.graphics.StrokeCap`, `androidx.compose.ui.graphics.graphicsLayer`,
`androidx.compose.ui.util.lerp`, `androidx.compose.ui.semantics.stateDescription`.

Previews: four — unticked/ticked × light/dark.

---

## 7. Navigation

**`AppDestinations.kt`**

```kotlin
// ScreenBaseRoutes
const val MEAL_PLAN_SHOPPING_LIST = "meal_plan_shopping_list"

// AppDestinations
MEAL_PLAN_SHOPPING_LIST(
    R.string.app_dest_title_shopping_list,
    "${ScreenBaseRoutes.MEAL_PLAN_SHOPPING_LIST}/{$MEAL_PLAN_ID_ARG}"
),

// NavigationActions
/** Opens the shopping list derived from an open meal plan. */
fun navigateToMealPlanShoppingList(mealPlanId: UUID) {
    navController.navigate("${ScreenBaseRoutes.MEAL_PLAN_SHOPPING_LIST}/$mealPlanId")
}
```

**`ChefAINavGraph.kt`** — destination, next to `MEAL_PLAN_DETAIL`:

```kotlin
composable(route = AppDestinations.MEAL_PLAN_SHOPPING_LIST.route) {
    ShoppingListScreen(snackbarHostState = snackbarHostState)
}
```

**The FAB.** It lives in the shared `Scaffold`, matching how the Recipes and Meal Plans FABs are
already handled, so it renders above the bottom nav for free. The Scaffold only tracks
`currentRoute`, so capture the plan id from the destination listener's third parameter (currently
discarded as `_`):

```kotlin
var currentMealPlanId by rememberSaveable { mutableStateOf<String?>(null) }
```

```kotlin
NavController.OnDestinationChangedListener { _, destination, arguments ->
    …
    if (destination.route == AppDestinations.MEAL_PLAN_DETAIL.route) {
        currentMealPlanId = arguments?.getString(AppDestinationArgs.MEAL_PLAN_ID_ARG)
    }
    …
}
```

and a branch in `floatingActionButton`:

```kotlin
AppDestinations.MEAL_PLAN_DETAIL.route -> {
    currentMealPlanId?.let { planId ->
        FloatingActionButton(
            onClick = { navActions.navigateToMealPlanShoppingList(UUID.fromString(planId)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = stringResource(R.string.shopping_list_open),
            )
        }
    }
}
```

`Icons.Default.ShoppingCart` → `androidx.compose.material.icons.filled.ShoppingCart` (core icon set,
already on the classpath). The FAB is shown unconditionally on the detail route — the nav graph
cannot see whether the plan has meals, and an empty plan lands on the list's empty state, which is
an acceptable outcome.

**`BottomNavigationBar.kt`** — keep the Meal Plans tab highlighted:

```kotlin
MEAL_PLANS(
    …
    childRoutePrefixes = setOf(
        ScreenBaseRoutes.MEAL_PLAN_DETAIL,
        ScreenBaseRoutes.MEAL_PLAN_RECIPE_DETAIL,
        ScreenBaseRoutes.MEAL_PLAN_SHOPPING_LIST,
    ),
),
```

`isRouteInSection` matches by `startsWith`, and `"meal_plan_shopping_list"` is not a prefix-match of
either existing entry, so this addition is required.

---

## 8. Strings

Add next to the existing `meal_plan_*` block in `app/src/main/res/values/strings.xml`:

```xml
<string name="app_dest_title_shopping_list">Shopping List</string>
<string name="shopping_list_open">Shopping list</string>
<string name="shopping_list_progress">%1$d of %2$d picked up</string>
<string name="shopping_list_uncheck_all">Uncheck all</string>
<string name="shopping_list_empty_title">Nothing to buy yet</string>
<string name="shopping_list_empty_subtitle">Build this plan first, or add ingredients to its recipes.</string>
<string name="shopping_list_toggle_error">Couldn\'t update this item</string>
<string name="shopping_list_clear_error">Couldn\'t clear the list</string>
```

---

## 9. Tests

Follow the project's Given/When/Then style, Truth assertions, Turbine for flows, JVM fakes over mocks.

**`GrocerySectionClassifierTest`** — at minimum:
- `eggplant` → `PRODUCE`, **not** `DAIRY_AND_EGGS` (the whole-token rule)
- `eggs` and `Egg` → `DAIRY_AND_EGGS`
- `cornstarch` → `SPICES_AND_BAKING`, `corn` → `PRODUCE`
- `frozen peas` → `FROZEN` (override beats the `pea` produce token)
- `canned chickpeas` → `PANTRY` (override beats the `chickpea` pantry token — same answer, but assert the override path)
- `coconut milk` → `PANTRY`, `almond milk` → `DAIRY_AND_EGGS`, `milk` → `DAIRY_AND_EGGS`
- `chicken stock` → `PANTRY`, `chicken breast` → `MEAT_AND_SEAFOOD`
- `Extra-Virgin Olive Oil` → `PANTRY` (normalisation across the hyphen and case)
- `unobtainium dust` → `OTHER`

**`ShoppingListBuilderTest`**:
- two recipes both needing "Onion" produce one item with the summed quantity
- `"Olive oil"` and `"olive oil"` collapse into one item
- a recipe filling two slots doubles its quantities
- `plannedServings = 4` against a `servings = 2` recipe doubles quantities; `recipeServings = 0`
  leaves them untouched
- mixed units render `"2 tbsp + 100 ml"`, ordered by unit with blank-unit last
- items with only zero quantities get `quantityLabel == null`
- sections come back in `GrocerySection` declaration order, empty sections dropped
- items inside a section are alphabetical, case-insensitively
- ticked items keep their alphabetical position and set `isChecked`
- `formatQuantity`: `2.0` → `"2"`, `0.5` → `"0.5"`, `1.005` → `"1"`, `1.25` → `"1.25"`
- blank display names are dropped

**`ShoppingListViewModelTest`** (Turbine + `FakeMealPlanRepository` + a new `FakeShoppingListRepository`):
- unknown plan id → `NotFound`
- a plan with no days → `Success` with `list.isEmpty`
- ticking an item flips `isChecked` on the next emission
- `onUncheckAll` clears every tick
- a repository failure emits `ShoppingListEvent.ShowError` and leaves state intact

**`FakeRecipeDao`** must gain `observeIngredientsForRecipes`; back it with the same in-memory maps
the fake already uses, applying the same `deletedAt IS NULL` filters as the real query.

**`ChefAIDatabaseMigrationTest`** (instrumented):
- `migrate6To7_addsShoppingListChecksTable` — create a v6 DB with one `meal_plans` row, run
  `runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)`, assert the plan row survives and that
  a row can be inserted into `shopping_list_checks`
- rename `migrateAll1To6_succeeds` → `migrateAll1To7_succeeds` and append `MIGRATION_6_7`

**`BottomNavigationBarTest`** — assert `isRouteInSection("meal_plan_shopping_list/<uuid>", MEAL_PLANS)`
is `true` and that no other tab claims it.

---

## 10. Order of work

1. Entity + DAO + `MIGRATION_6_7` + DB registration + Hilt provides. Compile, then diff your DDL
   against the generated `app/schemas/…/7.json` and fix it to match.
2. `RecipeDao.observeIngredientsForRecipes` + `PlanIngredientRow` + `FakeRecipeDao`.
3. Domain: `GrocerySection`, `GrocerySectionClassifier`, `PlannedIngredient`, `ShoppingList` +
   `ShoppingListBuilder`. Write `GrocerySectionClassifierTest` and `ShoppingListBuilderTest`
   alongside — these two carry almost all the real logic and are cheap to iterate on.
4. Repository interface + impl + Hilt binding + `FakeShoppingListRepository`.
5. ViewModel + its test.
6. `ShoppingListRow`, then `ShoppingListScreen`, with previews.
7. Navigation: destination, route, FAB, bottom-nav prefix, `BottomNavigationBarTest`.
8. Strings.
9. Migration test.
10. `./gradlew :app:testDebugUnitTest` — report pass/fail counts and fix anything red before calling
    this done. Instrumented migration tests need a device/emulator:
    `./gradlew :app:connectedDebugAndroidTest --tests '*ChefAIDatabaseMigrationTest*'`.

---

## 11. Follow-ups, explicitly out of scope

- Unit conversion and a real `unit` enum (`RecipeIngredientEntity.unit` still carries `// TODO Make enum`).
- A "hide meals I've already cooked" filter.
- Manual items ("add milk to this list") — needs its own table, not derivable from the plan.
- Sharing/exporting the list.
- Syncing ticks across devices.
- A `grocerySection` column on `ingredients` (the entity's existing `// TODO add grocery section ?`),
  which would want backend agreement and a sync-payload field.
