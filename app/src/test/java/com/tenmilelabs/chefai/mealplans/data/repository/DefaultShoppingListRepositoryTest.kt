package com.tenmilelabs.chefai.mealplans.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeShoppingListCheckDao
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
        repository = DefaultShoppingListRepository(recipeDao, checkDao)
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

    // --- observeCheckedItems / setChecked / clearChecks ---

    @Test
    fun `observeCheckedItems reflects a setChecked(true) call`() = runTest {
        val planId = UUID.randomUUID()

        repository.observeCheckedItems(planId).test {
            assertThat(awaitItem()).isEmpty()

            repository.setChecked(planId, "onion", checked = true)

            assertThat(awaitItem()).containsExactly("onion")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setChecked(false) removes a previously ticked item`() = runTest {
        val planId = UUID.randomUUID()
        repository.setChecked(planId, "onion", checked = true)

        repository.setChecked(planId, "onion", checked = false)

        assertThat(repository.observeCheckedItems(planId).first()).isEmpty()
    }

    @Test
    fun `clearChecks empties only the given plan's ticks`() = runTest {
        val planA = UUID.randomUUID()
        val planB = UUID.randomUUID()
        repository.setChecked(planA, "onion", checked = true)
        repository.setChecked(planB, "milk", checked = true)

        repository.clearChecks(planA)

        assertThat(repository.observeCheckedItems(planA).first()).isEmpty()
        assertThat(repository.observeCheckedItems(planB).first()).containsExactly("milk")
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
