package com.tenmilelabs.chefai.data

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.source.local.FakeRecipeDao
import com.tenmilelabs.chefai.data.source.network.FakeApiService
import com.tenmilelabs.chefai.testData.recipe1
import com.tenmilelabs.chefai.testData.recipe3
import com.tenmilelabs.chefai.testData.recipeEntity1
import com.tenmilelabs.chefai.testData.recipeEntity2
import com.tenmilelabs.chefai.testData.recipeEntity3
import com.tenmilelabs.chefai.testData.recipeId1
import com.tenmilelabs.chefai.testData.testIngredients
import com.tenmilelabs.chefai.testData.testLabels
import com.tenmilelabs.chefai.testData.testRecipeIngredients
import com.tenmilelabs.chefai.testData.testRecipeLabels
import com.tenmilelabs.chefai.testData.testRecipeTags
import com.tenmilelabs.chefai.testData.testSteps1
import com.tenmilelabs.chefai.testData.testSteps2
import com.tenmilelabs.chefai.testData.testSteps3
import com.tenmilelabs.chefai.testData.testTags
import com.tenmilelabs.chefai.testData.testUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DefaultRecipeRepositoryTest {

    // SUT
    private lateinit var recipeRepository: DefaultRecipeRepository

    // Dependencies
    private lateinit var localDataSource: FakeRecipeDao
    private lateinit var remoteDataSource: FakeApiService
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun createRepository() {
        localDataSource = FakeRecipeDao()
        remoteDataSource = FakeApiService()
        recipeRepository =
            DefaultRecipeRepository(localDataSource, remoteDataSource, testDispatcher, testScope, )

        // Seed the fake DAO with our test data.
        // This simulates a database with pre-existing data.
        localDataSource.seed(
            users = listOf(testUser),
            recipes = listOf(recipeEntity1, recipeEntity2, recipeEntity3),
            ingredients = testIngredients,
            labels = testLabels,
            tags = testTags,
            steps = testSteps1 + testSteps2 + testSteps3,
            recipeIngredients = testRecipeIngredients,
            recipeLabels = testRecipeLabels,
            recipeTags = testRecipeTags
        )
    }

    @Test
    fun `getRecipes() returns all recipes from local source`() = runTest {
        val recipes = recipeRepository.getRecipes()
        assertThat(recipes).hasSize(3)
        assertThat(recipes.map { it.uuid }).containsExactly(
            recipeEntity1.uuid,
            recipeEntity2.uuid,
            recipeEntity3.uuid
        )
    }

    @Test
    fun `getRecipe() returns correct recipe with details`() = runTest {
        val recipe = recipeRepository.getRecipe(recipeId1)
        assertThat(recipe).isNotNull()
        assertThat(recipe?.uuid).isEqualTo(recipeId1)
        assertThat(recipe?.title).isEqualTo(recipe1.title)
        assertThat(recipe?.ingredients).hasSize(2)
        assertThat(recipe?.ingredients?.map { it.displayName }).containsExactly("Flour", "Milk")
        assertThat(recipe?.steps).hasSize(3)
    }

    @Test
    fun `saveRecipe() creates a new recipe with all details`() = runTest {
        recipeRepository.createRecipe(recipe3)

        val savedRecipe = recipeRepository.getRecipe(recipe3.uuid)
        assertThat(savedRecipe).isNotNull()
        assertThat(savedRecipe?.title).isEqualTo(recipe3.title)
        assertThat(savedRecipe?.ingredients).hasSize(1) // recipe3 has one ingredient in test data
    }

    @Test
    fun `saveRecipe() updates an existing recipe`() = runTest {
        val updatedRecipe = recipe1.copy(title = "The Best Pancakes")
        recipeRepository.createRecipe(updatedRecipe)

        val savedRecipe = recipeRepository.getRecipe(recipe1.uuid)
        assertThat(savedRecipe?.title).isEqualTo("The Best Pancakes")
    }

    @Test
    fun `deleteRecipe() removes a recipe and its associations`() = runTest {
        // Given: A recipe with associations exists.
        val recipeBeforeDelete = recipeRepository.getRecipe(recipeId1)
        assertThat(recipeBeforeDelete).isNotNull()
        assertThat(recipeBeforeDelete?.ingredients).isNotEmpty()
        assertThat(recipeBeforeDelete?.steps).isNotEmpty()

        // When: The recipe is deleted.
        recipeRepository.deleteRecipe(recipeId1)

        // Then: The recipe is no longer available.
        val recipes = recipeRepository.getRecipes()
        assertThat(recipes.find { it.uuid == recipeId1 }).isNull()
        assertThat(recipes).hasSize(2)
        assertThat(recipeRepository.getRecipe(recipeId1)).isNull()

        // And When: A new recipe with the same ID but no associations is created.
        val newRecipeWithSameId = recipe1.copy(
            steps = emptyList(),
            ingredients = emptyList(),
            tags = emptyList(),
            labels = emptyList()
        )
        recipeRepository.createRecipe(newRecipeWithSameId)

        // Then: Fetching the recipe should not show the old, orphaned data.
        val recreatedRecipe = recipeRepository.getRecipe(recipeId1)
        assertThat(recreatedRecipe).isNotNull()
        assertThat(recreatedRecipe?.steps).isEmpty()
        assertThat(recreatedRecipe?.ingredients).isEmpty()
    }
}
