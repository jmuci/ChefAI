package com.tenmilelabs.chefai.home.data.repository

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeLabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeTagDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.home.data.model.SidecarIngredient
import com.tenmilelabs.chefai.home.data.model.SidecarStep
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.home.data.mapper.toRecipeEntity
import com.tenmilelabs.chefai.home.data.model.HomeSidecarDto
import com.tenmilelabs.chefai.home.data.model.SidecarCreatorDto
import com.tenmilelabs.chefai.home.data.model.SidecarLabelDto
import com.tenmilelabs.chefai.home.data.model.SidecarRecipeDto
import com.tenmilelabs.chefai.home.data.model.SidecarTagDto
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DefaultHomeRecipeSidecarRepositoryTest {

    private lateinit var recipeDao: FakeRecipeDao
    private lateinit var tagDao: FakeTagDao
    private lateinit var labelDao: FakeLabelDao
    private lateinit var userDao: FakeUserDao
    private lateinit var recipeTagDao: FakeRecipeTagCrossRefDao
    private lateinit var recipeLabelDao: FakeRecipeLabelCrossRefDao
    private lateinit var ingredientDao: FakeIngredientDao
    private lateinit var recipeIngredientDao: FakeRecipeIngredientDao
    private lateinit var recipeStepDao: FakeRecipeStepDao
    private lateinit var repository: DefaultHomeRecipeSidecarRepository

    private val creatorId = "dead0000-cafe-4000-8000-000000000001"
    private val tagId = "dead0000-cafe-4000-8000-000000000010"
    private val labelId = "dead0000-cafe-4000-8000-000000000020"
    private val recipe1Id = "dead0000-cafe-4000-8000-000000000101"
    private val recipe2Id = "dead0000-cafe-4000-8000-000000000102"

    private val sidecar = HomeSidecarDto(
        creators = listOf(SidecarCreatorDto(uuid = creatorId, displayName = "ChefAI Editorial")),
        tags = listOf(SidecarTagDto(uuid = tagId, displayName = "Quick")),
        labels = listOf(SidecarLabelDto(uuid = labelId, displayName = "Staff Pick")),
        recipes = listOf(
            SidecarRecipeDto(
                uuid = recipe1Id,
                title = "Herb-Crusted Chicken",
                description = "Juicy roasted chicken.",
                imageUrl = "https://example.com/chicken.jpg",
                imageUrlThumbnail = "https://example.com/chicken-thumb.jpg",
                prepTimeMinutes = 15,
                cookTimeMinutes = 40,
                servings = 4,
                creatorId = creatorId,
                privacy = "PUBLIC",
                updatedAt = 1773792000000L,
                tagIds = listOf(tagId),
                labelIds = listOf(labelId),
            ),
            SidecarRecipeDto(
                uuid = recipe2Id,
                title = "Mushroom Risotto",
                description = "Creamy risotto.",
                imageUrl = "https://example.com/risotto.jpg",
                imageUrlThumbnail = "https://example.com/risotto-thumb.jpg",
                prepTimeMinutes = 10,
                cookTimeMinutes = 30,
                servings = 2,
                creatorId = creatorId,
                privacy = "PUBLIC",
                updatedAt = 1773792000000L,
                tagIds = emptyList(),
                labelIds = emptyList(),
            ),
        ),
    )

    @Before
    fun setUp() {
        recipeDao = FakeRecipeDao()
        tagDao = FakeTagDao()
        labelDao = FakeLabelDao()
        userDao = FakeUserDao()
        recipeTagDao = FakeRecipeTagCrossRefDao()
        recipeLabelDao = FakeRecipeLabelCrossRefDao()
        ingredientDao = FakeIngredientDao()
        recipeIngredientDao = FakeRecipeIngredientDao()
        recipeStepDao = FakeRecipeStepDao()
        repository = DefaultHomeRecipeSidecarRepository(
            recipeDao = recipeDao,
            tagDao = tagDao,
            labelDao = labelDao,
            userDao = userDao,
            recipeTagCrossRefDao = recipeTagDao,
            recipeLabelCrossRefDao = recipeLabelDao,
            ingredientDao = ingredientDao,
            recipeIngredientDao = recipeIngredientDao,
            recipeStepDao = recipeStepDao,
            transactionRunner = FakeTransactionRunner(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `new recipes are written with SYNCED state and correct count returned`() = runTest {
        val written = repository.upsertSidecar(sidecar)

        assertThat(written).isEqualTo(2)
        val r1 = recipeDao.getRecipeById(UUID.fromString(recipe1Id))
        assertThat(r1).isNotNull()
        assertThat(r1!!.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(r1.title).isEqualTo("Herb-Crusted Chicken")
    }

    @Test
    fun `PENDING recipe is skipped`() = runTest {
        // Seed an existing PENDING recipe
        recipeDao.seed(recipes = listOf(
            sidecar.recipes.first().let { dto ->
                dto.toRecipeEntity()
                    .copy(syncState = SyncState.PENDING)
            }
        ))

        val written = repository.upsertSidecar(sidecar)

        assertThat(written).isEqualTo(1) // only recipe2 written
        // recipe1 must not have been overwritten
        val r1 = recipeDao.getRecipeById(UUID.fromString(recipe1Id))
        assertThat(r1!!.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `SYNCED recipe is skipped`() = runTest {
        recipeDao.seed(recipes = listOf(
            sidecar.recipes.first().let { dto ->
                dto.toRecipeEntity()
                    .copy(syncState = SyncState.SYNCED, title = "Old Title")
            }
        ))

        val written = repository.upsertSidecar(sidecar)

        assertThat(written).isEqualTo(1)
        // Title should not be overwritten
        val r1 = recipeDao.getRecipeById(UUID.fromString(recipe1Id))
        assertThat(r1!!.title).isEqualTo("Old Title")
    }

    @Test
    fun `tags and labels are upserted`() = runTest {
        repository.upsertSidecar(sidecar)

        val tag = tagDao.getTagById(UUID.fromString(tagId))
        assertThat(tag).isNotNull()
        assertThat(tag!!.displayName).isEqualTo("Quick")

        val label = labelDao.getLabelById(UUID.fromString(labelId))
        assertThat(label).isNotNull()
        assertThat(label!!.displayName).isEqualTo("Staff Pick")
    }

    @Test
    fun `creator is upserted before recipe`() = runTest {
        repository.upsertSidecar(sidecar)

        val creator = userDao.getUserById(UUID.fromString(creatorId))
        assertThat(creator).isNotNull()
        assertThat(creator!!.displayName).isEqualTo("ChefAI Editorial")
    }

    @Test
    fun `tag cross-refs are written for recipes with tags`() = runTest {
        repository.upsertSidecar(sidecar)

        val tags = recipeTagDao.getTagsForRecipe(UUID.fromString(recipe1Id))
        assertThat(tags).hasSize(1)
        assertThat(tags.first().tagId).isEqualTo(UUID.fromString(tagId))
        assertThat(tags.first().syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `label cross-refs are written for recipes with labels`() = runTest {
        repository.upsertSidecar(sidecar)

        val labels = recipeLabelDao.getLabelsForRecipe(UUID.fromString(recipe1Id))
        assertThat(labels).hasSize(1)
        assertThat(labels.first().labelId).isEqualTo(UUID.fromString(labelId))
        assertThat(labels.first().syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `returns zero when all recipes already exist`() = runTest {
        // Seed both as SYNCED
        sidecar.recipes.forEach { dto ->
            recipeDao.seed(recipes = listOf(
                dto.toRecipeEntity()
                    .copy(syncState = SyncState.SYNCED)
            ))
        }

        val written = repository.upsertSidecar(sidecar)
        assertThat(written).isEqualTo(0)
    }

    @Test
    fun `ingredients and recipe ingredient cross-refs are written for new recipes`() = runTest {
        val sidecarWithIngredients = sidecar.copy(
            recipes = listOf(
                sidecar.recipes[0].copy(
                    ingredients = listOf(
                        SidecarIngredient(name = "Chicken breast", quantity = 500.0, unit = "g"),
                        SidecarIngredient(name = "Olive oil", quantity = 2.0, unit = "tbsp"),
                    ),
                ),
            ),
        )

        repository.upsertSidecar(sidecarWithIngredients)

        val crossRefs = recipeIngredientDao.getIngredientsForRecipe(UUID.fromString(recipe1Id))
        assertThat(crossRefs).hasSize(2)
        assertThat(crossRefs.map { it.unit }).containsExactly("g", "tbsp")

        val ingredients = ingredientDao.getAllIngredients()
        assertThat(ingredients).hasSize(2)
        assertThat(ingredients.map { it.displayName }).containsExactly("Chicken breast", "Olive oil")
    }

    @Test
    fun `steps are written for new recipes`() = runTest {
        val sidecarWithSteps = sidecar.copy(
            recipes = listOf(
                sidecar.recipes[0].copy(
                    steps = listOf(
                        SidecarStep(orderIndex = 0, instruction = "Preheat oven to 200°C."),
                        SidecarStep(orderIndex = 1, instruction = "Coat chicken with herbs."),
                    ),
                ),
            ),
        )

        repository.upsertSidecar(sidecarWithSteps)

        val steps = recipeStepDao.getStepsForRecipe(UUID.fromString(recipe1Id))
        assertThat(steps).hasSize(2)
        assertThat(steps.map { it.orderIndex }).containsExactly(0, 1)
        assertThat(steps.first { it.orderIndex == 0 }.instruction).isEqualTo("Preheat oven to 200°C.")
    }

    @Test
    fun `ingredients and steps are not written for skipped recipes`() = runTest {
        recipeDao.seed(recipes = listOf(
            sidecar.recipes[0].toRecipeEntity().copy(syncState = SyncState.SYNCED)
        ))

        val sidecarWithData = sidecar.copy(
            recipes = listOf(
                sidecar.recipes[0].copy(
                    ingredients = listOf(SidecarIngredient(name = "Salt", quantity = 1.0, unit = "tsp")),
                    steps = listOf(SidecarStep(orderIndex = 0, instruction = "Season.")),
                ),
            ),
        )

        repository.upsertSidecar(sidecarWithData)

        assertThat(ingredientDao.getAllIngredients()).isEmpty()
        assertThat(recipeIngredientDao.getIngredientsForRecipe(UUID.fromString(recipe1Id))).isEmpty()
        assertThat(recipeStepDao.getStepsForRecipe(UUID.fromString(recipe1Id))).isEmpty()
    }
}
