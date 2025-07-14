package com.tenmilelabs.chefai.data

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.data.source.local.FakeRecipeDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DefaultRecipeRepositoryTest {

    private val recipe1 = Recipe(
        uuid = "1",
        title = "Title1",
        description = "Description1",
        imageUrl = "ImageUrl1",
        thumbnailUrl = "ThumbnailUrl1",
        prepTime = 10,
        recipeUrl = "RecipeUrl1",
        label = "Label1"
    )
    private val recipe2 = Recipe(
        uuid = "2",
        title = "Title2",
        description = "Description2",
        imageUrl = "ImageUrl2",
        thumbnailUrl = "ThumbnailUrl1",
        prepTime = 10,
        recipeUrl = "RecipeUrl2",
        label = "Label2"
    )
    private val recipe3 = Recipe(
        uuid = "3",
        title = "Title3",
        description = "Description3",
        imageUrl = "ImageUrl3",
        thumbnailUrl = "ThumbnailUrl3",
        prepTime = 10,
        recipeUrl = "RecipeUrl3",
        label = "Label3"
    )

    private val localRecipes =
        listOf(recipe1.toRecipeEntity(), recipe2.toRecipeEntity()).sortedBy { it.uuid }

    // Dependencies
    private lateinit var localDataSource: FakeRecipeDao
    private var testDispatcher = UnconfinedTestDispatcher()
    private var testScope = TestScope(testDispatcher)

    // Class under test
    private lateinit var recipeRepository: DefaultRecipeRepository

    @ExperimentalCoroutinesApi
    @Before
    fun createRepository() {
        localDataSource = FakeRecipeDao(localRecipes.toMutableList())
        recipeRepository = DefaultRecipeRepository(localDataSource, testDispatcher, testScope)
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
            assertThat(recipeRepository.getRecipes().size).isEqualTo(2)
        }
    }

    @Test
    fun getRecipesFlow() = testScope.runTest {
        assertThat(recipeRepository.getRecipesFlow().first().size).isEqualTo(2)
    }

    @Test
    fun getRecipeStream() = testScope.runTest {
        assertThat(recipeRepository.getRecipeFlow("1").first()).isEqualTo(recipe1)
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
        val newRecipe = Recipe(
            uuid = "2",
            title = "Title4",
            description = "Description4",
            imageUrl = "ImageUrl4",
            thumbnailUrl = "ThumbnailUrl4",
            prepTime = 10,
            recipeUrl = "RecipeUrl4",
            label = "Label4"
        )
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
        assertThat(localDataSource.recipes?.size).isEqualTo(1)
        assertThat(localDataSource.recipes?.map { it.uuid }).doesNotContain("1")

    }
}