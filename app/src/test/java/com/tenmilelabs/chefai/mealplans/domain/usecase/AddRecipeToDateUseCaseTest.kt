package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.domain.week.planDateFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class AddRecipeToDateUseCaseTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val clock: Clock = Clock.fixed(LocalDate.of(2026, 10, 28).atStartOfDay(zone).toInstant(), zone)
    private val userId: UUID = UUID.randomUUID()
    private val monday: LocalDate = LocalDate.of(2026, 10, 26)

    private val repository = FakeMealPlanRepository()
    private val useCase = AddRecipeToDateUseCase(repository, clock)

    // --- findTarget ---

    @Test
    fun `a date inside a plan's span resolves to that plan's day`() {
        val plan = plan(createdOn = monday, lengthDays = 5)

        val target = AddRecipeToDateUseCase.findTarget(listOf(plan), userId, monday.plusDays(3), zone)

        assertThat(target).isEqualTo(PlanDayTarget(plan.uuid, dayIndex = 3))
    }

    @Test
    fun `dates before or after a plan are not in its span`() {
        val plan = plan(createdOn = monday, lengthDays = 3)

        assertThat(AddRecipeToDateUseCase.findTarget(listOf(plan), userId, monday.minusDays(1), zone)).isNull()
        assertThat(AddRecipeToDateUseCase.findTarget(listOf(plan), userId, monday.plusDays(3), zone)).isNull()
    }

    @Test
    fun `a plan's span starts on its start date, not its creation date`() {
        val plan = plan(createdOn = monday, lengthDays = 3).copy(startDate = monday.plusDays(7))

        assertThat(AddRecipeToDateUseCase.findTarget(listOf(plan), userId, monday.plusDays(1), zone)).isNull()
        assertThat(AddRecipeToDateUseCase.findTarget(listOf(plan), userId, monday.plusDays(8), zone))
            .isEqualTo(PlanDayTarget(plan.uuid, dayIndex = 1))
    }

    @Test
    fun `days stored past the nominal length still count as the plan's span`() {
        val plan = plan(createdOn = monday, lengthDays = 3, days = listOf(day(5)))

        val target = AddRecipeToDateUseCase.findTarget(listOf(plan), userId, monday.plusDays(4), zone)

        assertThat(target?.dayIndex).isEqualTo(4)
    }

    @Test
    fun `draft, generating, archived and other users' plans are never written into`() {
        val others = listOf(
            plan(createdOn = monday, lengthDays = 7, status = MealPlanStatus.DRAFT),
            plan(createdOn = monday, lengthDays = 7, status = MealPlanStatus.GENERATING),
            plan(createdOn = monday, lengthDays = 7, status = MealPlanStatus.ARCHIVED),
            plan(createdOn = monday, lengthDays = 7, owner = UUID.randomUUID()),
        )

        assertThat(AddRecipeToDateUseCase.findTarget(others, userId, monday, zone)).isNull()
    }

    @Test
    fun `of two overlapping plans the older one is chosen`() {
        val older = plan(createdOn = monday, lengthDays = 7)
        val newer = plan(createdOn = monday.plusDays(2), lengthDays = 7)

        val target = AddRecipeToDateUseCase.findTarget(listOf(newer, older), userId, monday.plusDays(3), zone)

        assertThat(target?.planId).isEqualTo(older.uuid)
    }

    // --- invoke ---

    @Test
    fun `adds to the existing plan covering the date`() = runTest {
        val existing = plan(createdOn = monday, lengthDays = 5)
        repository.emitPlans(existing)
        val recipeId = UUID.randomUUID()

        val added = useCase(userId, listOf(existing), monday.plusDays(2), MealSlot.LUNCH, recipeId)

        assertThat(added).isTrue()
        val plans = repository.observeMealPlansForUser(userId).first()
        assertThat(plans).hasSize(1)
        val placed = plans.single().days.single { it.dayIndex == 2 }
        assertThat(placed.lunchRecipeId).isEqualTo(recipeId)
        assertThat(placed.dinnerRecipeId).isNull()
    }

    @Test
    fun `creates a one-day plan anchored on the date when none covers it`() = runTest {
        val date = monday.plusDays(10)
        val recipeId = UUID.randomUUID()

        val added = useCase(userId, emptyList(), date, MealSlot.DINNER, recipeId)

        assertThat(added).isTrue()
        val created = repository.observeMealPlansForUser(userId).first().single()
        assertThat(created.status).isEqualTo(MealPlanStatus.READY)
        assertThat(created.preferences.planLengthDays).isEqualTo(1)
        assertThat(created.name).isEqualTo(AddRecipeToDateUseCase.SINGLE_MEAL_PLAN_NAME)
        assertThat(created.days.single().dinnerRecipeId).isEqualTo(recipeId)
        assertThat(created.days.single().lunchRecipeId).isNull()
        // The point of anchoring: the week view must project it back onto the date it was added for.
        assertThat(planDateFor(created.createdAt, 0, zone)).isEqualTo(date)
        assertThat(created.startDate).isEqualTo(date)
    }

    @Test
    fun `the anchor date holds in a zone that is not UTC`() = runTest {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val tokyoUseCase = AddRecipeToDateUseCase(repository, Clock.fixed(clock.instant(), tokyo))
        val date = monday.plusDays(10)

        tokyoUseCase(userId, emptyList(), date, MealSlot.DINNER, UUID.randomUUID())

        val created = repository.observeMealPlansForUser(userId).first().single()
        assertThat(planDateFor(created.createdAt, 0, tokyo)).isEqualTo(date)
    }

    private fun day(index: Int) =
        MealPlanDay(UUID.randomUUID(), index, dinnerRecipeId = UUID.randomUUID(), lunchRecipeId = null)

    private fun plan(
        createdOn: LocalDate,
        lengthDays: Int,
        days: List<MealPlanDay> = emptyList(),
        status: MealPlanStatus = MealPlanStatus.READY,
        owner: UUID = userId,
    ): MealPlan {
        val millis = createdOn.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return MealPlan(
            uuid = UUID.randomUUID(),
            userId = owner,
            name = "Plan",
            preferences = MealPlanPreferences(
                planLengthDays = lengthDays,
                mealType = MealType.DINNER,
                dietaryRestrictions = emptySet(),
                recipeSource = RecipeSource.COLLECTION_ONLY,
                maxPrepTimeMinutes = null,
                servingsPerMeal = 2,
                batchCooking = false,
                leftoverFriendly = false,
                varietyPreference = VarietyPreference.MEDIUM,
            ),
            status = status,
            createdAt = millis,
            updatedAt = millis,
            days = days,
        )
    }
}
