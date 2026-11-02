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

    // --- Desserts ---

    @Test
    fun `desserts are never scheduled`() {
        val cake = recipe("Cheesecake", tags = listOf("Dessert"))
        val cookies = recipe("Cookies", labels = listOf("cookies"))
        val candidates = listOf(cake, cookies, recipe("Caesar Salad"), recipe("Lasagna"))

        val days = schedule(
            preferences(planLengthDays = 7, mealType = MealType.DINNER_AND_LUNCH),
            candidates,
        )

        val used = days.flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
        assertThat(used).containsNoneOf(cake.uuid, cookies.uuid)
    }

    @Test
    fun `a collection of only desserts produces no days`() {
        val days = schedule(preferences(), listOf(recipe("Brownies", tags = listOf("Desserts"))))

        assertThat(days).isEmpty()
    }

    @Test
    fun `dessert matching is by whole tag, not substring`() {
        val stew = recipe("Sweet Potato Stew", tags = listOf("Sweet Potato"))

        val days = schedule(preferences(planLengthDays = 2), listOf(stew))

        assertThat(days.mapNotNull { it.dinnerRecipeId }).containsExactly(stew.uuid, stew.uuid)
    }

    @Test
    fun `desserts are excluded even when a dietary fallback widens the pool`() {
        val cake = recipe("Cake", tags = listOf("Dessert"))
        val pasta = recipe("Pasta", tags = listOf("Italian"))

        val days = schedule(
            preferences(planLengthDays = 3, dietaryRestrictions = setOf(DietaryRestriction.KETO)),
            listOf(cake, pasta),
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }.toSet()).containsExactly(pasta.uuid)
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

    // --- Go-to recipes ---

    @Test
    fun `go-to recipes fill the earliest slots`() {
        val goTo = listOf(recipe("Go-to A"), recipe("Go-to B"))

        val days = schedule(
            preferences(planLengthDays = 7),
            pool(10) + goTo,
            goToRecipeIds = goTo.ids(),
        )

        val dinners = days.mapNotNull { it.dinnerRecipeId }
        assertThat(dinners.take(4).toSet()).isEqualTo(goTo.ids())
        assertThat(dinners.drop(4)).containsNoneIn(goTo.ids())
    }

    @Test
    fun `a go-to recipe repeats even when high variety would not repeat anything`() {
        val goTo = recipe("Go-to")

        val days = schedule(
            preferences(planLengthDays = 7, varietyPreference = VarietyPreference.HIGH),
            pool(10) + goTo,
            goToRecipeIds = setOf(goTo.uuid),
        )

        val dinners = days.mapNotNull { it.dinnerRecipeId }
        assertThat(dinners.count { it == goTo.uuid }).isEqualTo(MealPlanScheduler.GO_TO_SERVINGS_PER_WEEK)
        val others = dinners.filterNot { it == goTo.uuid }
        assertThat(others.toSet()).hasSize(others.size)
    }

    @Test
    fun `the go-to allowance scales with the plan length`() {
        val goTo = recipe("Go-to")

        val days = schedule(preferences(planLengthDays = 14), pool(20) + goTo, setOf(goTo.uuid))

        assertThat(days.count { it.dinnerRecipeId == goTo.uuid })
            .isEqualTo(2 * MealPlanScheduler.GO_TO_SERVINGS_PER_WEEK)
    }

    @Test
    fun `a go-to recipe is never served twice on the same day`() {
        val goTo = recipe("Go-to")

        val days = schedule(
            preferences(planLengthDays = 7, mealType = MealType.DINNER_AND_LUNCH),
            pool(5) + goTo,
            goToRecipeIds = setOf(goTo.uuid),
        )

        assertThat(days.all { it.lunchRecipeId != it.dinnerRecipeId }).isTrue()
        assertThat(days.count { it.lunchRecipeId == goTo.uuid || it.dinnerRecipeId == goTo.uuid })
            .isEqualTo(MealPlanScheduler.GO_TO_SERVINGS_PER_WEEK)
    }

    @Test
    fun `when every eligible recipe is a go-to they fill the whole plan`() {
        val goTo = listOf(recipe("Go-to A"), recipe("Go-to B"))

        val days = schedule(
            preferences(planLengthDays = 7, mealType = MealType.DINNER_AND_LUNCH),
            goTo,
            goToRecipeIds = goTo.ids(),
        )

        assertThat(days).hasSize(7)
        assertThat(days.all { it.lunchRecipeId != null && it.dinnerRecipeId != null }).isTrue()
        assertThat(days.all { it.lunchRecipeId != it.dinnerRecipeId }).isTrue()
    }

    @Test
    fun `a go-to recipe over the prep-time limit is not preferred`() {
        val slowGoTo = recipe("Slow go-to", prepTimeMinutes = 90)

        val days = schedule(
            preferences(planLengthDays = 5, maxPrepTimeMinutes = 20),
            pool(5) + slowGoTo,
            goToRecipeIds = setOf(slowGoTo.uuid),
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }).doesNotContain(slowGoTo.uuid)
    }

    @Test
    fun `a go-to recipe that misses a dietary restriction is not preferred`() {
        val steak = recipe("Steak", tags = listOf("Beef"))
        val vegan = (1..5).map { recipe("Vegan $it", tags = listOf("Vegan")) }

        val days = schedule(
            preferences(planLengthDays = 5, dietaryRestrictions = setOf(DietaryRestriction.VEGAN)),
            vegan + steak,
            goToRecipeIds = setOf(steak.uuid),
        )

        assertThat(days.mapNotNull { it.dinnerRecipeId }).doesNotContain(steak.uuid)
    }

    @Test
    fun `a go-to dessert is still never scheduled`() {
        val cake = recipe("Cake", tags = listOf("Dessert"))

        val days = schedule(preferences(planLengthDays = 5), pool(5) + cake, setOf(cake.uuid))

        assertThat(days.mapNotNull { it.dinnerRecipeId }).doesNotContain(cake.uuid)
    }

    @Test
    fun `go-to ids absent from the candidates leave the schedule unchanged`() {
        val candidates = pool(10)
        val preferences = preferences(planLengthDays = 7, mealType = MealType.DINNER_AND_LUNCH)

        val withoutGoTo = schedule(preferences, candidates)
        val withUnknownGoTo = schedule(preferences, candidates, setOf(UUID.randomUUID()))

        assertThat(withUnknownGoTo.map { it.lunchRecipeId to it.dinnerRecipeId })
            .isEqualTo(withoutGoTo.map { it.lunchRecipeId to it.dinnerRecipeId })
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
        goToRecipeIds: Set<UUID> = emptySet(),
        seed: Int = 42,
    ) = MealPlanScheduler.schedule(
        preferences = preferences,
        candidates = candidates,
        goToRecipeIds = goToRecipeIds,
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

    private fun List<RecipePreview>.ids() = map { it.uuid }.toSet()

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
