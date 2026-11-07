package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.data.repository.FakeSavedMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class RepeatSavedMealPlanUseCaseTest {

    private val userId = UUID.randomUUID()
    private val startDate = LocalDate.of(2026, 11, 9)
    private val clock = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC)

    private lateinit var savedRepository: FakeSavedMealPlanRepository
    private lateinit var mealPlanRepository: FakeMealPlanRepository
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var useCase: RepeatSavedMealPlanUseCase

    @Before
    fun setup() {
        savedRepository = FakeSavedMealPlanRepository()
        mealPlanRepository = FakeMealPlanRepository()
        recipesRepository = FakeRecipesRepository()
        useCase = RepeatSavedMealPlanUseCase(savedRepository, mealPlanRepository, recipesRepository, clock)
    }

    @Test
    fun `given a saved plan, when repeated, then the lineup is copied under new day ids with no cooked marks`() = runTest {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val saved = savedPlan(
            SavedMealPlanDay(0, lunchRecipeId = a, dinnerRecipeId = b),
            SavedMealPlanDay(1, lunchRecipeId = null, dinnerRecipeId = a),
        )
        savedRepository.emitPlans(saved)

        val planId = useCase(userId, saved.uuid, startDate).getOrThrow()

        val plan = requireNotNull(mealPlanRepository.observeMealPlan(planId).first())
        assertThat(plan.days.map { Triple(it.dayIndex, it.lunchRecipeId, it.dinnerRecipeId) })
            .containsExactly(Triple(0, a, b), Triple(1, null, a))
        assertThat(plan.days.map { it.uuid }.distinct()).hasSize(2)
        assertThat(plan.days.all { it.lunchCookedAt == null && it.dinnerCookedAt == null }).isTrue()
    }

    @Test
    fun `given a saved plan, when repeated, then name, preferences, start date and timestamps follow the template`() = runTest {
        val saved = savedPlan(SavedMealPlanDay(0, null, UUID.randomUUID()), SavedMealPlanDay(1, null, UUID.randomUUID()))
        savedRepository.emitPlans(saved)

        val plan = requireNotNull(
            mealPlanRepository.observeMealPlan(useCase(userId, saved.uuid, startDate).getOrThrow()).first(),
        )

        assertThat(plan.name).isEqualTo(saved.title)
        assertThat(plan.preferences).isEqualTo(saved.preferences)
        assertThat(plan.preferences.planLengthDays).isEqualTo(plan.days.size)
        assertThat(plan.startDate).isEqualTo(startDate)
        assertThat(plan.userId).isEqualTo(userId)
        assertThat(plan.status).isEqualTo(MealPlanStatus.READY)
        assertThat(plan.createdAt).isEqualTo(clock.millis())
        assertThat(mealPlanRepository.generationRequestedIds).isEmpty()
        assertThat(mealPlanRepository.statelessGenerationRequestedIds).isEmpty()
    }

    @Test
    fun `given a locally deleted recipe, when repeated, then its slot is dropped but an unknown id is kept`() = runTest {
        val deleted = UUID.randomUUID()
        val unknown = UUID.randomUUID()
        val saved = savedPlan(SavedMealPlanDay(0, lunchRecipeId = deleted, dinnerRecipeId = unknown))
        savedRepository.emitPlans(saved)
        recipesRepository.softDeletedIds += deleted

        val plan = requireNotNull(
            mealPlanRepository.observeMealPlan(useCase(userId, saved.uuid, startDate).getOrThrow()).first(),
        )

        assertThat(plan.days.single().lunchRecipeId).isNull()
        assertThat(plan.days.single().dinnerRecipeId).isEqualTo(unknown)
    }

    @Test
    fun `given a missing template, when repeated, then it fails and creates nothing`() = runTest {
        val result = useCase(userId, UUID.randomUUID(), startDate)

        assertThat(result.isFailure).isTrue()
        assertThat(mealPlanRepository.observeMealPlansForUser(userId).first()).isEmpty()
    }

    private fun savedPlan(vararg days: SavedMealPlanDay) = SavedMealPlan(
        uuid = UUID.randomUUID(),
        userId = userId,
        title = "Easy weeknights",
        heroImagePath = null,
        preferences = MealPlanPreferences(
            planLengthDays = days.size,
            mealType = MealType.DINNER,
            dietaryRestrictions = emptySet(),
            recipeSource = RecipeSource.COLLECTION_ONLY,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        days = days.toList(),
        sourceMealPlanId = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}
