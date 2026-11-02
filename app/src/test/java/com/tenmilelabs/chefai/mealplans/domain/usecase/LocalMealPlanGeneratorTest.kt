package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.collections.data.repository.FakeCollectionsRepository
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import com.tenmilelabs.chefai.core.testutil.recipePreview2
import com.tenmilelabs.chefai.core.testutil.recipePreview3
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class LocalMealPlanGeneratorTest {

    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var mealPlanRepository: FakeMealPlanRepository
    private lateinit var collectionsRepository: FakeCollectionsRepository
    private lateinit var generator: LocalMealPlanGenerator

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        mealPlanRepository = FakeMealPlanRepository()
        collectionsRepository = FakeCollectionsRepository()
        generator = LocalMealPlanGenerator(recipesRepository, mealPlanRepository, collectionsRepository)
    }

    @Test
    fun `fills the plan from local recipes and marks it ready`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(
            listOf(recipePreview1, recipePreview2, recipePreview3)
        )
        val plan = plan(planLengthDays = 3)
        mealPlanRepository.createMealPlan(plan)

        val result = generator(plan)

        assertThat(result.getOrNull()).isEqualTo(3)
        val saved = mealPlanRepository.observeMealPlan(plan.uuid).first()!!
        assertThat(saved.days).hasSize(3)
        assertThat(saved.status).isEqualTo(MealPlanStatus.READY)
    }

    @Test
    fun `every generated day gets a dinner`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))
        val plan = plan(planLengthDays = 4)
        mealPlanRepository.createMealPlan(plan)

        generator(plan)

        val saved = mealPlanRepository.observeMealPlan(plan.uuid).first()!!
        assertThat(saved.days.all { it.dinnerRecipeId != null }).isTrue()
    }

    @Test
    fun `a lunch-and-dinner plan fills both slots`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(
            listOf(recipePreview1, recipePreview2, recipePreview3)
        )
        val plan = plan(planLengthDays = 2, mealType = MealType.DINNER_AND_LUNCH)
        mealPlanRepository.createMealPlan(plan)

        generator(plan)

        val saved = mealPlanRepository.observeMealPlan(plan.uuid).first()!!
        assertThat(saved.days.all { it.lunchRecipeId != null && it.dinnerRecipeId != null }).isTrue()
    }

    @Test
    fun `fails and writes nothing when the device holds no recipes`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(emptyList())
        val plan = plan()
        mealPlanRepository.createMealPlan(plan)

        val result = generator(plan)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .isInstanceOf(LocalMealPlanGenerator.NoRecipesAvailableException::class.java)
        assertThat(mealPlanRepository.locallyGeneratedIds).isEmpty()
        assertThat(mealPlanRepository.observeMealPlan(plan.uuid).first()!!.days).isEmpty()
    }

    @Test
    fun `a collection-only plan draws from the user's own recipes`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(
            listOf(recipePreview1, recipePreview2, recipePreview3)
        )
        val plan = plan(planLengthDays = 2, recipeSource = RecipeSource.COLLECTION_ONLY)
        mealPlanRepository.createMealPlan(plan)

        val result = generator(plan)

        assertThat(result.getOrNull()).isEqualTo(2)
        val saved = mealPlanRepository.observeMealPlan(plan.uuid).first()!!
        assertThat(saved.days).hasSize(2)
    }

    @Test
    fun `nothing in a freshly generated plan is marked cooked`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))
        val plan = plan(planLengthDays = 3, mealType = MealType.DINNER_AND_LUNCH)
        mealPlanRepository.createMealPlan(plan)

        generator(plan)

        val saved = mealPlanRepository.observeMealPlan(plan.uuid).first()!!
        assertThat(saved.days.all { it.lunchCookedAt == null && it.dinnerCookedAt == null }).isTrue()
    }

    @Test
    fun `go-to bookmarks are scheduled first`() = runTest {
        val goTo = mainCourse("Go-to")
        val others = (1..10).map { mainCourse("Recipe $it") }
        recipesRepository.setRecipePreviewsToEmit(others + goTo)
        collectionsRepository.setBookmarks(
            listOf(
                RecipeBookmark(goTo.uuid, isGoTo = true),
                // Bookmarked but not go-to: dealt like any other recipe.
                RecipeBookmark(others.first().uuid),
            )
        )
        val plan = plan(planLengthDays = 7)
        mealPlanRepository.createMealPlan(plan)

        generator(plan)

        val dinners = requireNotNull(mealPlanRepository.observeMealPlan(plan.uuid).first())
            .days.sortedBy { it.dayIndex }.map { it.dinnerRecipeId }
        assertThat(dinners.first()).isEqualTo(goTo.uuid)
        assertThat(dinners.count { it == goTo.uuid })
            .isEqualTo(MealPlanScheduler.GO_TO_SERVINGS_PER_WEEK)
    }

    @Test
    fun `without go-to bookmarks the plan still fills from every recipe`() = runTest {
        val recipes = (1..3).map { mainCourse("Recipe $it") }
        recipesRepository.setRecipePreviewsToEmit(recipes)
        collectionsRepository.setBookmarkedIds(recipes.map { it.uuid }.toSet())
        val plan = plan(planLengthDays = 3)
        mealPlanRepository.createMealPlan(plan)

        generator(plan)

        val saved = requireNotNull(mealPlanRepository.observeMealPlan(plan.uuid).first())
        assertThat(saved.days.mapNotNull { it.dinnerRecipeId }.toSet())
            .isEqualTo(recipes.map { it.uuid }.toSet())
    }

    /** A fixture recipe with no tags or labels, so neither the dessert rule nor a filter drops it. */
    private fun mainCourse(title: String): RecipePreview =
        recipePreview2.copy(uuid = UUID.randomUUID(), title = title, tags = emptyList(), labels = emptyList())

    private fun plan(
        planLengthDays: Int = 5,
        mealType: MealType = MealType.DINNER,
        recipeSource: RecipeSource = RecipeSource.INCLUDE_PUBLIC,
    ) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = "$planLengthDays-day meal plan",
        preferences = MealPlanPreferences(
            planLengthDays = planLengthDays,
            mealType = mealType,
            dietaryRestrictions = setOf(DietaryRestriction.NONE),
            recipeSource = recipeSource,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        status = MealPlanStatus.DRAFT,
        createdAt = 0L,
        updatedAt = 0L,
        days = emptyList(),
    )
}
