package com.tenmilelabs.chefai.mealplans.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeMealPlanDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanResponse
import com.tenmilelabs.chefai.mealplans.data.network.FakeMealPlanNetworkDataSource
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class DefaultMealPlanRepositoryTest {

    private lateinit var mealPlanDao: FakeMealPlanDao
    private lateinit var networkDataSource: FakeMealPlanNetworkDataSource
    private lateinit var syncScheduler: FakeSyncManager
    private lateinit var repository: DefaultMealPlanRepository

    @Before
    fun setup() {
        mealPlanDao = FakeMealPlanDao()
        networkDataSource = FakeMealPlanNetworkDataSource()
        syncScheduler = FakeSyncManager()
        repository = DefaultMealPlanRepository(mealPlanDao, networkDataSource, syncScheduler)
    }

    // --- observeMealPlan / observeMealPlansForUser reactivity ---
    // The whole point of combining the plan and day flows (rather than reading days once per
    // meal_plans emission) is that a cooked toggle - which only touches meal_plan_days - re-renders.

    @Test
    fun `observeMealPlan emits again when a meal is marked cooked`() = runTest {
        val plan = planWithOneDay()
        repository.createMealPlan(plan)
        val dayId = plan.days.single().uuid

        repository.observeMealPlan(plan.uuid).test {
            assertThat(awaitItem()!!.days.single().dinnerCookedAt).isNull()

            repository.setMealCooked(dayId, MealSlot.DINNER, cooked = true)

            assertThat(awaitItem()!!.days.single().dinnerCookedAt).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeMealPlansForUser emits again when a meal in one of the plans is marked cooked`() = runTest {
        val plan = planWithOneDay()
        repository.createMealPlan(plan)
        val dayId = plan.days.single().uuid

        repository.observeMealPlansForUser(plan.userId).test {
            assertThat(awaitItem().single().days.single().lunchCookedAt).isNull()

            repository.setMealCooked(dayId, MealSlot.LUNCH, cooked = true)

            assertThat(awaitItem().single().days.single().lunchCookedAt).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeMealPlansForUser does not include another user's plan`() = runTest {
        val mine = planWithOneDay(userId = UUID.randomUUID())
        val theirs = planWithOneDay(userId = UUID.randomUUID())
        repository.createMealPlan(mine)
        repository.createMealPlan(theirs)

        val plans = repository.observeMealPlansForUser(mine.userId).first()

        assertThat(plans.map { it.uuid }).containsExactly(mine.uuid)
    }

    // --- observeMealPlanDay ---

    @Test
    fun `observeMealPlanDay emits the day by its own id, independent of the plan`() = runTest {
        val plan = planWithOneDay()
        repository.createMealPlan(plan)
        val dayId = plan.days.single().uuid

        val day = repository.observeMealPlanDay(dayId).first()

        assertThat(day?.uuid).isEqualTo(dayId)
    }

    @Test
    fun `observeMealPlanDay emits null for an unknown day id`() = runTest {
        val day = repository.observeMealPlanDay(UUID.randomUUID()).first()

        assertThat(day).isNull()
    }

    @Test
    fun `observeMealPlanDay reflects a cooked toggle`() = runTest {
        val plan = planWithOneDay()
        repository.createMealPlan(plan)
        val dayId = plan.days.single().uuid

        repository.observeMealPlanDay(dayId).test {
            assertThat(awaitItem()?.dinnerCookedAt).isNull()

            repository.setMealCooked(dayId, MealSlot.DINNER, cooked = true)

            assertThat(awaitItem()?.dinnerCookedAt).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- setMealCooked ---

    @Test
    fun `setMealCooked marks only the requested slot`() = runTest {
        val plan = planWithOneDay()
        repository.createMealPlan(plan)
        val dayId = plan.days.single().uuid

        repository.setMealCooked(dayId, MealSlot.DINNER, cooked = true)

        val day = repository.observeMealPlan(plan.uuid).first()!!.days.single()
        assertThat(day.dinnerCookedAt).isNotNull()
        assertThat(day.lunchCookedAt).isNull()
    }

    @Test
    fun `setMealCooked with cooked=false clears a previously set mark`() = runTest {
        val plan = planWithOneDay()
        repository.createMealPlan(plan)
        val dayId = plan.days.single().uuid
        repository.setMealCooked(dayId, MealSlot.DINNER, cooked = true)

        repository.setMealCooked(dayId, MealSlot.DINNER, cooked = false)

        val day = repository.observeMealPlan(plan.uuid).first()!!.days.single()
        assertThat(day.dinnerCookedAt).isNull()
    }

    // --- saveLocallyGeneratedDays ---

    @Test
    fun `saveLocallyGeneratedDays replaces days, marks the plan ready, and requests sync`() = runTest {
        val plan = plan(days = emptyList())
        repository.createMealPlan(plan)
        val newDays = listOf(day(dayIndex = 0), day(dayIndex = 1))
        val syncCountBefore = syncScheduler.mutationSyncCount

        repository.saveLocallyGeneratedDays(plan.uuid, newDays)

        val saved = repository.observeMealPlan(plan.uuid).first()!!
        assertThat(saved.days.map { it.uuid }).containsExactlyElementsIn(newDays.map { it.uuid })
        assertThat(saved.status).isEqualTo(MealPlanStatus.READY)
        assertThat(syncScheduler.mutationSyncCount).isEqualTo(syncCountBefore + 1)
    }

    @Test
    fun `saveLocallyGeneratedDays is a no-op for a plan that does not exist`() = runTest {
        val syncCountBefore = syncScheduler.mutationSyncCount

        repository.saveLocallyGeneratedDays(UUID.randomUUID(), listOf(day(dayIndex = 0)))

        assertThat(syncScheduler.mutationSyncCount).isEqualTo(syncCountBefore)
    }

    // --- requestGeneration ---

    @Test
    fun `requestGeneration updates local status and syncState on success`() = runTest {
        val plan = plan(days = emptyList())
        repository.createMealPlan(plan)
        networkDataSource.response = GenerateMealPlanResponse(
            uuid = plan.uuid.toString(),
            status = "GENERATING",
            updatedAt = 999L,
        )

        val result = repository.requestGeneration(plan.uuid)

        assertThat(result.isSuccess).isTrue()
        assertThat(networkDataSource.requestedMealPlanIds).containsExactly(plan.uuid.toString())
        val updated = mealPlanDao.getMealPlanById(plan.uuid)!!
        assertThat(updated.status).isEqualTo("GENERATING")
        assertThat(updated.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `requestGeneration returns failure without throwing when the network call fails`() = runTest {
        val plan = plan(days = emptyList())
        repository.createMealPlan(plan)
        networkDataSource.exception = RuntimeException("network down")

        val result = repository.requestGeneration(plan.uuid)

        assertThat(result.isFailure).isTrue()
        val untouched = mealPlanDao.getMealPlanById(plan.uuid)!!
        assertThat(untouched.status).isEqualTo(MealPlanStatus.DRAFT.name)
    }

    // --- Helpers ---

    private fun planWithOneDay(userId: UUID = UUID.randomUUID()) =
        plan(userId = userId, days = listOf(day(dayIndex = 0)))

    private fun day(
        dayIndex: Int,
        dinnerRecipeId: UUID = UUID.randomUUID(),
        lunchRecipeId: UUID = UUID.randomUUID(),
    ) = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = dayIndex,
        dinnerRecipeId = dinnerRecipeId,
        lunchRecipeId = lunchRecipeId,
    )

    private fun plan(
        userId: UUID = UUID.randomUUID(),
        days: List<MealPlanDay>,
    ) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = userId,
        name = "Test plan",
        preferences = MealPlanPreferences(
            planLengthDays = days.size,
            mealType = MealType.DINNER_AND_LUNCH,
            dietaryRestrictions = setOf(DietaryRestriction.NONE),
            recipeSource = RecipeSource.INCLUDE_PUBLIC,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        status = MealPlanStatus.DRAFT,
        createdAt = 0L,
        updatedAt = 0L,
        days = days,
    )
}
