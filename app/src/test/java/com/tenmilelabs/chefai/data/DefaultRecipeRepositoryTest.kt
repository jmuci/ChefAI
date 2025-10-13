package com.tenmilelabs.chefai.data

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.data.mapper.toRoomEntity
import com.tenmilelabs.chefai.data.mapper.toNetwork
import com.tenmilelabs.chefai.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.source.local.FakeRecipeDao
import com.tenmilelabs.chefai.data.source.local.room.UserEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.data.source.network.FakeApiService
import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class DefaultRecipeRepositoryTest {

    // Test Data
    private val testUser = User(UUID.randomUUID(), "Test User", "test@test.com", null)
    private val testUserEntity = UserEntity(testUser.uuid, testUser.displayName, testUser.email, testUser.avatarUrl, System.currentTimeMillis(), null, com.tenmilelabs.chefai.data.source.local.util.SyncState.SYNCED)

    private val recipe1 = Recipe(UUID.randomUUID(), "Title 1", "Desc 1", "url1", "thumb1", 10, 10, 2, testUser, "extUrl1", emptyList(), emptyList(), emptyList(), emptyList(), System.currentTimeMillis())
    private val recipe2 = Recipe(UUID.randomUUID(), "Title 2", "Desc 2", "url2", "thumb2", 20, 20, 4, testUser, "extUrl2", emptyList(), emptyList(), emptyList(), emptyList(), System.currentTimeMillis())
    private val testRecipes = listOf(recipe1, recipe2)

    private val recipeEntity1 = recipe1.toRoomEntity()
    private val recipeEntity2 = recipe2.toRoomEntity()
    private val testRecipeEntities = listOf(recipeEntity1, recipeEntity2)

    // Dependencies
    private lateinit var localDataSource: FakeRecipeDao
    private lateinit var remoteDataSource: FakeApiService
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Class under test
    private lateinit var recipeRepository: DefaultRecipeRepository

    @Before
    fun createRepository() {
        localDataSource = FakeRecipeDao(testRecipeEntities.toMutableList(), listOf(testUserEntity))
        remoteDataSource = FakeApiService()
        recipeRepository =
            DefaultRecipeRepository(localDataSource, remoteDataSource, testDispatcher, testScope)
    }

    @Test
    fun `getRecipes() returns recipes from local source`() = testScope.runTest {
        val recipes = recipeRepository.getRecipes()
        assertThat(recipes).hasSize(2)
        assertThat(recipes).containsExactly(recipe1, recipe2).inOrder()
    }

    @Test
    fun `getRecipesStream() returns recipes from network`() = testScope.runTest {
        remoteDataSource.recipes = testRecipes.map { it.toNetwork() }
        val recipes = recipeRepository.getRecipesStream().first()
        // Note: The assertion is simplified because the network DTO is not as rich as the domain model
        assertThat(recipes.map { it.uuid }).isEqualTo(testRecipes.map { it.uuid })
        assertThat(recipes).hasSize(2)
    }

    @Test
    fun `getRecipe() returns correct recipe`() = testScope.runTest {
        val recipe = recipeRepository.getRecipe(recipe1.uuid)
        assertThat(recipe).isEqualTo(recipe1)
    }

    @Test
    fun `createRecipe() new recipe is saved`() = testScope.runTest {
        val newRecipe = Recipe(UUID.randomUUID(), "New Title", "...", "url", "thumb", 5, 5, 1, testUser, "ext", emptyList(), emptyList(), emptyList(), emptyList(), System.currentTimeMillis())
        val newId = recipeRepository.createRecipe(newRecipe)

        val savedRecipe = localDataSource.getRecipeById(newId)
        assertThat(savedRecipe?.title).isEqualTo("New Title")
        assertThat(savedRecipe?.uuid).isEqualTo(newId)
    }

    @Test
    fun `updateRecipe() updates existing recipe`() = testScope.runTest {
        val updatedRecipe = recipe1.copy(title = "New Title")
        recipeRepository.updateRecipe(updatedRecipe)

        val savedRecipe = localDataSource.getRecipeById(recipe1.uuid)
        assertThat(savedRecipe?.title).isEqualTo("New Title")
    }

    @Test
    fun `deleteAllRecipes() clears local data source`() = testScope.runTest {
        recipeRepository.deleteAllRecipes()
        assertThat(localDataSource.recipes).isEmpty()
    }

    @Test
    fun `deleteRecipe() removes correct recipe`() = testScope.runTest {
        recipeRepository.deleteRecipe(recipe1.uuid)
        assertThat(localDataSource.recipes).hasSize(1)
        assertThat(localDataSource.recipes?.first()?.uuid).isEqualTo(recipe2.uuid)
    }
}