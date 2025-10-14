package com.tenmilelabs.chefai.data

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.source.local.FakeRecipeDao
import com.tenmilelabs.chefai.data.source.network.FakeApiService
import com.tenmilelabs.chefai.testData.recipeEntity1
import com.tenmilelabs.chefai.testData.recipeEntity2
import com.tenmilelabs.chefai.testData.recipeEntity3
import com.tenmilelabs.chefai.testData.testIngredients
import com.tenmilelabs.chefai.testData.testLabels
import com.tenmilelabs.chefai.testData.testRecipeIngredients
import com.tenmilelabs.chefai.testData.testSteps1
import com.tenmilelabs.chefai.testData.testSteps2
import com.tenmilelabs.chefai.testData.testSteps3
import com.tenmilelabs.chefai.testData.testTags
import com.tenmilelabs.chefai.testData.testUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
            recipeLabels = listOf(recipeLabel1, recipeLabel2),
            recipeTags = listOf(recipeTag1, recipeTag2)
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
        val recipe = recipeRepository.getRecipe(recipeId1).first()
        assertThat(recipe).isNotNull()
        assertThat(recipe?.uuid).isEqualTo(recipeId1)
        assertThat(recipe?.title).isEqualTo(recipe1.title)
        assertThat(recipe?.ingredients).hasSize(2)
        assertThat(recipe?.ingredients?.map { it.displayName }).containsExactly("Flour", "Milk")
        assertThat(recipe?.steps).hasSize(3)
    }

    @Test
    fun `saveRecipe() creates a new recipe with all details`() = runTest {
        recipeRepository.saveRecipe(recipe3)

        val savedRecipe = recipeRepository.getRecipe(recipe3.uuid).first()
        assertThat(savedRecipe).isNotNull()
        assertThat(savedRecipe?.title).isEqualTo(recipe3.title)
        assertThat(savedRecipe?.ingredients).hasSize(0) // recipe3 has no ingredients in test data
    }

    @Test
    fun `saveRecipe() updates an existing recipe`() = runTest {
        val updatedRecipe = recipe1.copy(title = "The Best Pancakes")
        recipeRepository.saveRecipe(updatedRecipe)

        val savedRecipe = recipeRepository.getRecipe(recipe1.uuid).first()
        assertThat(savedRecipe?.title).isEqualTo("The Best Pancakes")
    }

    @Test
    fun `deleteRecipe() removes a recipe and its associations`() = runTest {
        recipeRepository.deleteRecipe(recipeId1)

        val recipes = recipeRepository.getRecipes().first()
        assertThat(recipes.find { it.uuid == recipeId1 }).isNull()
        assertThat(recipes).hasSize(2)

        // You could add more assertions here to ensure related data (steps, etc.) was deleted
    }
}
