package com.tenmilelabs.chefai.mealplans.domain.summary

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import org.junit.Test
import java.util.UUID

class SavedMealPlanSummaryBuilderTest {

    @Test
    fun `the user's own photo fronts the card as a file uri`() {
        // Given a plan with a custom photo and a recipe that also has one
        val withImage = preview("Carbonara", image = "https://img/carbonara.jpg")
        val plan = savedPlan(days = listOf(day(0, dinner = withImage.uuid)), heroImagePath = "/data/x/plan.jpg")

        // When
        val summary = SavedMealPlanSummaryBuilder.build(listOf(plan), mapOf(withImage.uuid to withImage)).single()

        // Then the custom photo wins
        assertThat(summary.heroImageModel).isEqualTo("file:///data/x/plan.jpg")
    }

    @Test
    fun `without a photo the first recipe that has an image fronts the card`() {
        val bare = preview("Plain")
        val local = preview("Local", localImage = "/data/x/local.jpg")
        val remote = preview("Remote", image = "https://img/remote.jpg")
        val plan = savedPlan(days = listOf(day(0, dinner = bare.uuid), day(1, dinner = local.uuid), day(2, dinner = remote.uuid)))

        val summary = SavedMealPlanSummaryBuilder.build(
            listOf(plan),
            listOf(bare, local, remote).associateBy { it.uuid },
        ).single()

        assertThat(summary.heroImageModel).isEqualTo("file:///data/x/local.jpg")
    }

    @Test
    fun `no photo anywhere leaves the hero null`() {
        val bare = preview("Plain")
        val plan = savedPlan(days = listOf(day(0, dinner = bare.uuid)))

        val summary = SavedMealPlanSummaryBuilder.build(listOf(plan), mapOf(bare.uuid to bare)).single()

        assertThat(summary.heroImageModel).isNull()
    }

    @Test
    fun `at most three titles are listed but every recipe is counted`() {
        val recipes = (1..5).map { preview("Recipe $it") }
        val plan = savedPlan(days = recipes.mapIndexed { i, r -> day(i, dinner = r.uuid) })

        val summary = SavedMealPlanSummaryBuilder.build(listOf(plan), recipes.associateBy { it.uuid }).single()

        assertThat(summary.recipeTitles).containsExactly("Recipe 1", "Recipe 2", "Recipe 3").inOrder()
        assertThat(summary.recipeCount).isEqualTo(5)
    }

    @Test
    fun `a recipe repeated across days counts once and dayCount is the lineup length`() {
        val a = preview("A")
        val plan = savedPlan(
            days = listOf(day(0, dinner = a.uuid), day(1, dinner = a.uuid), day(2, dinner = null)),
            mealType = MealType.DINNER_AND_LUNCH,
        )

        val summary = SavedMealPlanSummaryBuilder.build(listOf(plan), mapOf(a.uuid to a)).single()

        assertThat(summary.recipeCount).isEqualTo(1)
        assertThat(summary.dayCount).isEqualTo(3)
        assertThat(summary.mealType).isEqualTo(MealType.DINNER_AND_LUNCH)
        assertThat(summary.title).isEqualTo("My plan")
    }

    @Test
    fun `recipes not on this device are counted but not titled`() {
        val missing = UUID.randomUUID()
        val plan = savedPlan(days = listOf(day(0, dinner = missing)))

        val summary = SavedMealPlanSummaryBuilder.build(listOf(plan), emptyMap()).single()

        assertThat(summary.recipeTitles).isEmpty()
        assertThat(summary.recipeCount).isEqualTo(1)
    }

    private fun day(index: Int, dinner: UUID? = null, lunch: UUID? = null) =
        SavedMealPlanDay(dayIndex = index, lunchRecipeId = lunch, dinnerRecipeId = dinner)

    private fun savedPlan(
        days: List<SavedMealPlanDay>,
        heroImagePath: String? = null,
        mealType: MealType = MealType.DINNER,
    ) = SavedMealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        title = "My plan",
        heroImagePath = heroImagePath,
        preferences = MealPlanPreferences(
            planLengthDays = days.size,
            mealType = mealType,
            dietaryRestrictions = emptySet(),
            recipeSource = RecipeSource.COLLECTION_ONLY,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        days = days,
        sourceMealPlanId = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun preview(title: String, image: String = "", localImage: String? = null) = RecipePreview(
        uuid = UUID.randomUUID(),
        title = title,
        description = "",
        imageUrlThumbnail = image,
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        servings = 4,
        creatorId = UUID.randomUUID(),
        tags = emptyList(),
        labels = emptyList(),
        localImagePath = localImage,
    )
}
