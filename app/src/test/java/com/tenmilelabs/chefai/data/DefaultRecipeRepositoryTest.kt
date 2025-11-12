package com.tenmilelabs.chefai.data

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.data.mapper.toNetwork
import com.tenmilelabs.chefai.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.source.local.FakeRecipeDao
import com.tenmilelabs.chefai.data.source.local.room.UserEntity
import com.tenmilelabs.chefai.data.source.network.FakeApiService
import com.tenmilelabs.chefai.domain.model.User
import com.tenmilelabs.chefai.testData.TEST_DOMAIN_RECIPES_LIST
import com.tenmilelabs.chefai.testData.TEST_ROOM_RECIPES_LIST
import com.tenmilelabs.chefai.testData.recipe1
import com.tenmilelabs.chefai.testData.recipe2
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

    // Dependencies
    private lateinit var localDataSource: FakeRecipeDao
    private lateinit var remoteDataSource: FakeApiService
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Class under test
    private lateinit var recipeRepository: DefaultRecipeRepository

    @Before
    fun createRepository() {
        localDataSource = FakeRecipeDao(TEST_ROOM_RECIPES_LIST.toMutableList(), listOf(testUserEntity))
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
        remoteDataSource.fakeRecipes = TEST_DOMAIN_RECIPES_LIST.map { it.toNetwork() }
        val recipes = recipeRepository.getRecipesStream().first()
        // Note: The assertion is simplified because the network DTO is not as rich as the domain model
        assertThat(recipes.map { it.uuid }).isEqualTo(TEST_DOMAIN_RECIPES_LIST.map { it.uuid })
        assertThat(recipes).hasSize(2)
    }

    @Test
    fun `getRecipe() returns correct recipe`() = testScope.runTest {
        val recipe = recipeRepository.getRecipe(recipe1.uuid)
        assertThat(recipe).isEqualTo(recipe1)
    }

    @Test
    fun `createRecipe() new recipe is saved`() = testScope.runTest {
        val uuid = UUID.randomUUID()
        val newId = recipeRepository.createRecipe(recipe1)

        val savedRecipe = localDataSource.getRecipeById(uuid = uuid)
        assertThat(savedRecipe?.title).isEqualTo(recipe1.title)
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