package com.tenmilelabs.chefai.data

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.data.mapper.toRoomEntity
import com.tenmilelabs.chefai.data.mapper.toNetwork
import com.tenmilelabs.chefai.data.mapper.toRecipeEntity
import com.tenmilelabs.chefai.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.source.local.FakeRecipeDao
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.network.FakeApiService
import com.tenmilelabs.chefai.data.source.network.NetworkRecipe
import com.tenmilelabs.chefai.domain.model.Recipe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DefaultRecipeRepositoryTest {

    private val localRecipes: List<RecipeEntity> = TEST_RECIPES_LIST.toRoomEntity().sortedBy { it.uuid }
    private val networkRecipes: List<NetworkRecipe> = TEST_RECIPES_LIST.toNetwork().sortedBy { it.uuid }

    // Dependencies
    private lateinit var localDataSource: FakeRecipeDao
    private lateinit var remoteDataSource: FakeApiService
    private var testDispatcher = UnconfinedTestDispatcher()
    private var testScope = TestScope(testDispatcher)

    // Class under test
    private lateinit var recipeRepository: DefaultRecipeRepository

    @ExperimentalCoroutinesApi
    @Before
    fun createRepository() {
        localDataSource = FakeRecipeDao(localRecipes.toMutableList())
        remoteDataSource = FakeApiService(networkRecipes.toMutableList())
        recipeRepository =
            DefaultRecipeRepository(localDataSource, remoteDataSource, testDispatcher, testScope)
    }

    @Test
    fun getRecipes_emptyRepository() = testScope.runTest {
        localDataSource.deleteAllRecipes()
        assertThat(recipeRepository.getRecipes().size).isEqualTo(0)
    }

    @Test
    fun getRecipes_localSourceHasTwoRecipes() {
        testScope.runTest {
            localRecipes
            assertThat(recipeRepository.getRecipes().size).isEqualTo(3)
        }
    }

    @Test
    fun getRecipesFlow() = testScope.runTest {
        networkRecipes
        assertThat(recipeRepository.getRecipesStream().first().size).isEqualTo(3)
    }

    @Test
    fun getRecipeStream() = testScope.runTest {
        assertThat(recipeRepository.getRecipeStream("1").first()).isEqualTo(recipe1)
    }

    @Test
    fun getRecipe() = testScope.runTest {
        assertThat(recipeRepository.getRecipe("1")).isEqualTo(recipe1)
    }

    @Test
    fun createRecipe_newRecipeSavedInLocalSource() = testScope.runTest {
        val newRecipe = Recipe(
            uuid = "uuid4",
            title = "Title4",
            description = "Description4",
            imageUrl = "ImageUrl4",
            thumbnailUrl = "ThumbnailUrl4",
            prepTime = 10,
            recipeUrl = "RecipeUrl4",
            label = "Label4"
        )
        val newRecipeId = recipeRepository.createRecipe(newRecipe, "uuid4")
        assertThat(newRecipe.toRecipeEntity()).isIn(localDataSource.recipes)
        assertThat(localDataSource.recipes?.map { it.uuid }).contains(newRecipeId)
    }

    @Test
    fun updateRecipe() = testScope.runTest {
        val newRecipe = recipe4.copy(uuid = "2", title = "New Title")
        recipeRepository.updateRecipe(newRecipe)
        assertThat(localDataSource.recipes?.contains(newRecipe.toRecipeEntity()))
    }

    @Test
    fun deleteAllRecipes() = testScope.runTest {
        recipeRepository.deleteAllRecipes()
        assertThat(localDataSource.recipes?.size).isEqualTo(0)
    }

    @Test
    fun deleteRecipe() = testScope.runTest {
        recipeRepository.deleteRecipe("1")
        assertThat(localDataSource.recipes?.size).isEqualTo(2)
        assertThat(localDataSource.recipes?.map { it.uuid }).doesNotContain("1")
    }
}