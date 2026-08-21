package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

class MealPlanSchedulerTest {

    // --- Shape of the produced plan ---

    @Test
    fun `empty candidate list produces no days`() {
        val days = schedule(preferences(), candidates = emptyList())

        assertThat(days).isEmpty()
    }

    @Test
    fun `produces one day per planned day, indexed from zero`() {
        val days = schedule(preferences(planLengthDays = 5), pool(10))

        assertThat(days).hasSize(5)
        assertThat(days.map { it.dayIndex }).containsExactly(0, 1, 2, 3, 4).inOrder()
        assertThat(days.map { it.uuid }.toSet()).hasSize(5)
    }

    @Test
    fun `dinner-only plan fills dinner and leaves lunch empty`() {
        val days = schedule(preferences(mealType = MealType.DINNER), pool(10))

        assertThat(days.all { it.dinnerRecipeId != null }).isTrue()
        assertThat(days.all { it.lunchRecipeId == null }).isTrue()
    }

    @Test
    fun `lunch and dinner plan fills both slots with different recipes`() {
        val days = schedule(
            preferences(planLengthDays = 4, mealType = MealType.DINNER_AND_LUNCH),
            pool(10),
        )

        assertThat(days.all { it.lunchRecipeId != null && it.dinnerRecipeId != null }).isTrue()
        assertThat(days.all { it.lunchRecipeId != it.dinnerRecipeId }).isTrue()
    }

    @Test
    fun `no meal starts out cooked`() {
        val days = schedule(preferences(mealType = MealType.DINNER_AND_LUNCH), pool(10))

        assertThat(days.all { it.dinnerCookedAt == null && it.lunchCookedAt == null }).isTrue()
    }

    @Test
    fun `a plan of zero days produces nothing`() {
        assertThat(schedule(preferences(planLengthDays = 0), pool(5))).isEmpty()
    }

    // --- Rotation ---

    @Test
    fun `a pool larger than the plan never repeats a recipe`() {
        val days = schedule(
            preferences(planLengthDays = 4, mealType = MealType.DINNER_AND_LUNCH),
            pool(10),
        )

        val used = days.flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
        assertThat(used.toSet()).hasSize(used.size)
    }

    @Test
    fun `a small pool cycles through every recipe before repeating one`() {
        val days = schedule(preferences(planLengthDays = 6), pool(3))

        assertThat(days).hasSize(6)
        assertThat(days.take(3).mapNotNull { it.dinnerRecipeId }.toSet()).hasSize(3)
    }

    @Test
    fun `a single recipe still fills every day`() {
        val days = schedule(preferences(planLengthDays = 3), pool(1))

        assertThat(days).hasSize(3)
        assertThat(days.all { it.dinnerRecipeId != null }).isTrue()
    }

    // --- Preference filters ---

    @Test
    fun `recipes over the prep-time limit are left out`() {
        val quick = recipe("Quick", prepTimeMinutes = 10)
        val slow = recipe("Slow", prepTimeMinutes = 90)

        val days = schedule(
            preferences(planLengthDays = 6, maxPrepTimeMinutes = 20),
            listOf(quick, recipe("Quick2", prepTimeMinutes = 15), slow),
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }).doesNotContain(slow.uuid)
    }

    @Test
    fun `a prep-time limit nothing meets falls back to the full pool`() {
        val days = schedule(
            preferences(planLengthDays = 3, maxPrepTimeMinutes = 5),
            listOf(recipe("A", prepTimeMinutes = 90), recipe("B", prepTimeMinutes = 80)),
        )

        assertThat(days).hasSize(3)
        assertThat(days.all { it.dinnerRecipeId != null }).isTrue()
    }

    @Test
    fun `a dietary restriction excludes recipes that do not declare it`() {
        val steak = recipe("Steak", tags = listOf("Beef"))
        val candidates = listOf(recipe("Vegan Bowl", tags = listOf("Vegan")), steak)

        val days = schedule(
            preferences(planLengthDays = 4, dietaryRestrictions = setOf(DietaryRestriction.VEGAN)),
            candidates,
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }).doesNotContain(steak.uuid)
    }

    @Test
    fun `a vegan recipe satisfies a vegetarian restriction`() {
        val steak = recipe("Steak", tags = listOf("Beef"))
        val candidates = listOf(recipe("Vegan Curry", tags = listOf("vegan")), steak)

        val days = schedule(
            preferences(
                planLengthDays = 3,
                dietaryRestrictions = setOf(DietaryRestriction.VEGETARIAN),
            ),
            candidates,
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }).doesNotContain(steak.uuid)
    }

    @Test
    fun `restrictions are matched against labels as well as tags`() {
        val glutenFree = recipe("GF Pasta", labels = listOf("Gluten-Free"))
        val other = recipe("Sourdough", tags = listOf("Bread"))

        val days = schedule(
            preferences(
                planLengthDays = 3,
                dietaryRestrictions = setOf(DietaryRestriction.GLUTEN_FREE),
            ),
            listOf(glutenFree, other),
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }).doesNotContain(other.uuid)
    }

    @Test
    fun `NONE is not treated as a restriction`() {
        val days = schedule(
            preferences(planLengthDays = 3, dietaryRestrictions = setOf(DietaryRestriction.NONE)),
            pool(5),
        )

        assertThat(days).hasSize(3)
    }

    @Test
    fun `a restriction nothing matches falls back to the full pool`() {
        val days = schedule(
            preferences(planLengthDays = 3, dietaryRestrictions = setOf(DietaryRestriction.KETO)),
            listOf(recipe("Pasta", tags = listOf("Italian"))),
        )

        assertThat(days).hasSize(3)
        assertThat(days.all { it.dinnerRecipeId != null }).isTrue()
    }

    // --- Variety ---

    @Test
    fun `low variety rotates a narrow set of favourites`() {
        val days = schedule(
            preferences(planLengthDays = 9, varietyPreference = VarietyPreference.LOW),
            pool(10),
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }.toSet().size).isAtMost(3)
    }

    @Test
    fun `high variety draws on more of the collection than low variety does`() {
        val high = schedule(
            preferences(planLengthDays = 9, varietyPreference = VarietyPreference.HIGH),
            pool(10),
        )

        assertThat(high.mapNotNull { it.dinnerRecipeId }.toSet().size).isGreaterThan(3)
    }

    // --- Determinism ---

    @Test
    fun `the same seed produces the same schedule`() {
        val candidates = pool(10)

        val first = schedule(preferences(), candidates, seed = 7).map { it.dinnerRecipeId }
        val second = schedule(preferences(), candidates, seed = 7).map { it.dinnerRecipeId }

        assertThat(first).isEqualTo(second)
    }

    // --- Helpers ---

    private fun schedule(
        preferences: MealPlanPreferences,
        candidates: List<RecipePreview>,
        seed: Int = 42,
    ) = MealPlanScheduler.schedule(
        preferences = preferences,
        candidates = candidates,
        newDayId = UUID::randomUUID,
        random = Random(seed),
    )

    private fun preferences(
        planLengthDays: Int = 5,
        mealType: MealType = MealType.DINNER,
        dietaryRestrictions: Set<DietaryRestriction> = emptySet(),
        maxPrepTimeMinutes: Int? = null,
        varietyPreference: VarietyPreference = VarietyPreference.HIGH,
    ) = MealPlanPreferences(
        planLengthDays = planLengthDays,
        mealType = mealType,
        dietaryRestrictions = dietaryRestrictions,
        recipeSource = RecipeSource.INCLUDE_PUBLIC,
        maxPrepTimeMinutes = maxPrepTimeMinutes,
        servingsPerMeal = 2,
        batchCooking = false,
        leftoverFriendly = false,
        varietyPreference = varietyPreference,
    )

    private fun pool(size: Int) = (1..size).map { recipe("Recipe $it") }

    private fun recipe(
        title: String,
        prepTimeMinutes: Int = 10,
        tags: List<String> = emptyList(),
        labels: List<String> = emptyList(),
    ) = RecipePreview(
        uuid = UUID.randomUUID(),
        title = title,
        description = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = 5,
        servings = 2,
        creatorId = UUID.randomUUID(),
        tags = tags.map { Tag(UUID.randomUUID(), it) },
        labels = labels.map { Label(UUID.randomUUID(), it) },
    )
}
