package com.tenmilelabs.chefai.mealplans.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeShoppingListCheckDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class DefaultShoppingListRepositoryTest {

    private lateinit var recipeDao: FakeRecipeDao
    private lateinit var checkDao: FakeShoppingListCheckDao
    private lateinit var syncScheduler: FakeSyncManager
    private lateinit var repository: DefaultShoppingListRepository

    private val user = UserEntity(
        uuid = UUID.randomUUID(),
        displayName = "Chef",
        email = "chef@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    @Before
    fun setup() {
        recipeDao = FakeRecipeDao()
        checkDao = FakeShoppingListCheckDao()
        syncScheduler = FakeSyncManager()
        repository = DefaultShoppingListRepository(recipeDao, checkDao, syncScheduler)
    }

    // --- observeIngredientsForRecipes ---

    @Test
    fun `observeIngredientsForRecipes emits empty immediately for an empty id list`() = runTest {
        val result = repository.observeIngredientsForRecipes(emptyList()).first()

        assertThat(result).isEmpty()
    }

    @Test
    fun `observeIngredientsForRecipes maps every DAO field onto the domain model`() = runTest {
        val recipe = recipe(servings = 4)
        val salt = ingredient("Salt")
        seed(recipe, salt, quantity = 2.0, unit = "tbsp")

        val row = repository.observeIngredientsForRecipes(listOf(recipe.uuid)).first().single()

        assertThat(row.recipeId).isEqualTo(recipe.uuid)
        assertThat(row.recipeServings).isEqualTo(4)
        assertThat(row.displayName).isEqualTo("Salt")
        assertThat(row.quantity).isEqualTo(2.0)
        assertThat(row.unit).isEqualTo("tbsp")
    }

    // --- observeCheckedState / setChecked / clearChecks ---

    @Test
    fun `observeCheckedState reflects a setChecked(true) call`() = runTest {
        val planId = UUID.randomUUID()

        repository.observeCheckedState(planId).test {
            assertThat(awaitItem().checkedKeys).isEmpty()

            repository.setChecked(planId, "onion", checked = true)

            assertThat(awaitItem().checkedKeys).containsExactly("onion")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setChecked(false) removes a previously ticked item from the checked set`() = runTest {
        val planId = UUID.randomUUID()
        repository.setChecked(planId, "onion", checked = true)

        repository.setChecked(planId, "onion", checked = false)

        assertThat(repository.observeCheckedState(planId).first().checkedKeys).isEmpty()
    }

    @Test
    fun `setChecked preserves an existing row's deletedAt instead of resurrecting it`() = runTest {
        // A pulled deletion can leave deletedAt set on a row whose itemKey is still shown by
        // ShoppingListBuilder (it derives the visible list from the plan's live ingredients,
        // independently of this row) — tapping that checkbox must not silently undo the deletion.
        val planId = UUID.randomUUID()
        checkDao.upsert(
            ShoppingListCheckEntity(
                mealPlanId = planId,
                itemKey = "onion",
                checkedAt = 1_000L,
                checked = true,
                deletedAt = 5_000L,
                syncState = SyncState.SYNCED,
            )
        )

        repository.setChecked(planId, "onion", checked = false)

        val stored = checkDao.getCheck(planId, "onion")
        assertThat(stored?.checked).isFalse()
        assertThat(stored?.deletedAt).isEqualTo(5_000L)
    }

    @Test
    fun `setChecked(false) upserts the row rather than deleting it — an unchecked item is still on the list`() = runTest {
        val planId = UUID.randomUUID()
        repository.setChecked(planId, "onion", checked = true)

        repository.setChecked(planId, "onion", checked = false)

        val stored = checkDao.getCheck(planId, "onion")
        assertThat(stored).isNotNull()
        assertThat(stored?.checked).isFalse()
    }

    @Test
    fun `setChecked stamps the row PENDING and requests a mutation sync`() = runTest {
        val planId = UUID.randomUUID()

        repository.setChecked(planId, "onion", checked = true)

        assertThat(checkDao.getCheck(planId, "onion")?.syncState).isEqualTo(SyncState.PENDING)
        assertThat(syncScheduler.mutationSyncCount).isEqualTo(1)
    }

    @Test
    fun `clearChecks empties only the given plan's ticks`() = runTest {
        val planA = UUID.randomUUID()
        val planB = UUID.randomUUID()
        repository.setChecked(planA, "onion", checked = true)
        repository.setChecked(planB, "milk", checked = true)

        repository.clearChecks(planA)

        assertThat(repository.observeCheckedState(planA).first().checkedKeys).isEmpty()
        assertThat(repository.observeCheckedState(planB).first().checkedKeys).containsExactly("milk")
    }

    @Test
    fun `clearChecks does not resurrect a soft-deleted row`() = runTest {
        val planId = UUID.randomUUID()
        checkDao.upsert(
            ShoppingListCheckEntity(
                mealPlanId = planId,
                itemKey = "onion",
                checkedAt = 1_000L,
                checked = true,
                deletedAt = 5_000L,
                syncState = SyncState.SYNCED,
            )
        )

        repository.clearChecks(planId)

        val stored = checkDao.getCheck(planId, "onion")
        assertThat(stored?.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(stored?.deletedAt).isEqualTo(5_000L)
    }

    @Test
    fun `clearChecks upserts rows to unchecked-PENDING and requests a sync, rather than deleting them`() = runTest {
        val planId = UUID.randomUUID()
        repository.setChecked(planId, "onion", checked = true)

        repository.clearChecks(planId)

        val stored = checkDao.getCheck(planId, "onion")
        assertThat(stored).isNotNull()
        assertThat(stored?.checked).isFalse()
        assertThat(stored?.syncState).isEqualTo(SyncState.PENDING)
        assertThat(syncScheduler.mutationSyncCount).isEqualTo(2)
    }

    // --- observeCheckedState: checkedByUserIds ---

    @Test
    fun `observeCheckedState's checkedByUserIds is empty until a pull resolves who checked an item`() = runTest {
        val planId = UUID.randomUUID()
        repository.setChecked(planId, "onion", checked = true)

        assertThat(repository.observeCheckedState(planId).first().checkedByUserIds).isEmpty()
    }

    @Test
    fun `observeCheckedState's checkedByUserIds reflects a pulled item's checkedBy`() = runTest {
        val planId = UUID.randomUUID()
        val checkerId = UUID.randomUUID()
        checkDao.upsert(
            ShoppingListCheckEntity(
                mealPlanId = planId,
                itemKey = "onion",
                checkedAt = 1_000L,
                checked = true,
                checkedBy = checkerId,
                syncState = SyncState.SYNCED,
            )
        )

        assertThat(repository.observeCheckedState(planId).first().checkedByUserIds)
            .containsExactly("onion", checkerId)
    }

    @Test
    fun `clearChecks removes the plan's checkers along with its ticks`() = runTest {
        val planId = UUID.randomUUID()
        val checkerId = UUID.randomUUID()
        checkDao.upsert(
            ShoppingListCheckEntity(
                mealPlanId = planId,
                itemKey = "onion",
                checkedAt = 1_000L,
                checked = true,
                checkedBy = checkerId,
                syncState = SyncState.SYNCED,
            )
        )

        repository.clearChecks(planId)

        assertThat(repository.observeCheckedState(planId).first().checkedByUserIds).isEmpty()
    }

    // --- Helpers ---

    private fun recipe(servings: Int = 4) = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = "Test recipe",
        description = "",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = servings,
        creatorId = user.uuid,
        recipeExternalUrl = null,
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun ingredient(name: String) = IngredientEntity(
        uuid = UuidV7Generator.newId(),
        displayName = name,
        allergenId = null,
        sourcePrimaryId = null,
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun seed(recipe: RecipeEntity, ingredient: IngredientEntity, quantity: Double, unit: String) {
        recipeDao.seed(
            users = listOf(user),
            recipes = listOf(recipe),
            ingredients = listOf(ingredient),
            recipeIngredients = listOf(
                RecipeIngredientEntity(
                    recipeId = recipe.uuid,
                    ingredientId = ingredient.uuid,
                    quantity = quantity,
                    unit = unit,
                    updatedAt = 0L,
                )
            ),
        )
    }
}
