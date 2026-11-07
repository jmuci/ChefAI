package com.tenmilelabs.chefai.mealplans.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeMealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeSavedMealPlanDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import com.tenmilelabs.chefai.mealplans.data.local.SavedPlanImageStore
import com.tenmilelabs.chefai.mealplans.data.mapper.toJson
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class DefaultSavedMealPlanRepositoryTest {

    private lateinit var savedDao: FakeSavedMealPlanDao
    private lateinit var mealPlanDao: FakeMealPlanDao
    private lateinit var syncScheduler: FakeSyncManager
    private lateinit var imageStore: SavedPlanImageStore
    private lateinit var repository: DefaultSavedMealPlanRepository

    private val userId = UUID.randomUUID()

    @Before
    fun setup() {
        savedDao = FakeSavedMealPlanDao()
        mealPlanDao = FakeMealPlanDao()
        syncScheduler = FakeSyncManager()
        imageStore = mockk(relaxed = true)
        repository = DefaultSavedMealPlanRepository(savedDao, mealPlanDao, syncScheduler, imageStore)
    }

    @Test
    fun `saveFromMealPlan snapshots the lineup in day order and the source plan id`() = runTest {
        // Given a 3-day plan whose days are stored out of order
        val planId = givenPlan(
            days = listOf(day(2), day(0), day(1)),
            preferences = preferences(planLengthDays = 7),
        )
        val dayOne = mealPlanDao.getDaysForMealPlan(planId).first { it.dayIndex == 1 }

        // When
        val saved = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "Week", null).getOrThrow()

        // Then
        assertThat(saved.days.map { it.dayIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(saved.days[1].dinnerRecipeId).isEqualTo(dayOne.dinnerRecipeId)
        assertThat(saved.days[1].lunchRecipeId).isEqualTo(dayOne.lunchRecipeId)
        assertThat(saved.sourceMealPlanId).isEqualTo(planId)
        assertThat(repository.getSavedPlan(saved.uuid)).isEqualTo(saved)
    }

    @Test
    fun `saveFromMealPlan drops cooked marks by keeping only recipe ids`() = runTest {
        val planId = givenPlan(days = listOf(day(0, cooked = true)))

        val saved = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "Week", null).getOrThrow()

        assertThat(saved.days.single().dinnerRecipeId).isNotNull()
        // Cooked marks are not representable on SavedMealPlanDay, nor in the persisted JSON.
        assertThat(savedDao.all.single().daysJson).doesNotContain("Cooked")
    }

    @Test
    fun `saveFromMealPlan sets planLengthDays to the number of days and keeps other preferences`() = runTest {
        val planId = givenPlan(
            days = listOf(day(0), day(1), day(2)),
            preferences = preferences(planLengthDays = 7, servings = 4),
        )

        val saved = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "Week", null).getOrThrow()

        assertThat(saved.preferences.planLengthDays).isEqualTo(3)
        assertThat(saved.preferences.servingsPerMeal).isEqualTo(4)
    }

    @Test
    fun `saveFromMealPlan trims the title, stores the image path, and queues PENDING plus a sync`() = runTest {
        val planId = givenPlan(days = listOf(day(0)))
        val id = UUID.randomUUID()

        repository.saveFromMealPlan(id, userId, planId, "  Lazy week  ", "/files/img").getOrThrow()

        val row = savedDao.all.single()
        assertThat(row.uuid).isEqualTo(id)
        assertThat(row.title).isEqualTo("Lazy week")
        assertThat(row.heroImagePath).isEqualTo("/files/img")
        assertThat(row.syncState).isEqualTo(SyncState.PENDING)
        assertThat(syncScheduler.mutationSyncCount).isEqualTo(1)
    }

    @Test
    fun `saveFromMealPlan fails for a plan with no days and writes nothing`() = runTest {
        val planId = givenPlan(days = emptyList())

        val result = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "Week", null)

        assertThat(result.isFailure).isTrue()
        assertThat(savedDao.all).isEmpty()
        assertThat(syncScheduler.mutationSyncCount).isEqualTo(0)
    }

    @Test
    fun `saveFromMealPlan fails for a missing or deleted plan`() = runTest {
        val missing = repository.saveFromMealPlan(UUID.randomUUID(), userId, UUID.randomUUID(), "Week", null)
        val deletedId = givenPlan(days = listOf(day(0)))
        mealPlanDao.softDelete(deletedId, 5L)
        val deleted = repository.saveFromMealPlan(UUID.randomUUID(), userId, deletedId, "Week", null)

        assertThat(missing.isFailure).isTrue()
        assertThat(deleted.isFailure).isTrue()
        assertThat(savedDao.all).isEmpty()
    }

    @Test
    fun `saveFromMealPlan fails on a blank title`() = runTest {
        val planId = givenPlan(days = listOf(day(0)))

        val result = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "   ", null)

        assertThat(result.isFailure).isTrue()
        assertThat(savedDao.all).isEmpty()
    }

    @Test
    fun `saveFromMealPlan fails on a title over 60 characters but accepts exactly 60`() = runTest {
        val planId = givenPlan(days = listOf(day(0)))

        val tooLong = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "x".repeat(61), null)
        val exact = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "x".repeat(60), null)

        assertThat(tooLong.isFailure).isTrue()
        assertThat(exact.isSuccess).isTrue()
        assertThat(savedDao.all).hasSize(1)
    }

    @Test
    fun `delete soft-deletes as PENDING, hides the plan, removes the image and requests sync`() = runTest {
        val planId = givenPlan(days = listOf(day(0)))
        val id = UUID.randomUUID()
        repository.saveFromMealPlan(id, userId, planId, "Week", "/files/img").getOrThrow()
        repository.observeSavedPlans(userId).test {
            assertThat(awaitItem()).hasSize(1)

            repository.delete(id)

            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        val row = savedDao.all.single()
        assertThat(row.deletedAt).isNotNull()
        assertThat(row.syncState).isEqualTo(SyncState.PENDING)
        assertThat(repository.getSavedPlan(id)).isNull()
        coVerify { imageStore.delete(id) }
        assertThat(syncScheduler.mutationSyncCount).isEqualTo(2)
    }

    @Test
    fun `saved plan survives deletion of the source meal plan`() = runTest {
        val planId = givenPlan(days = listOf(day(0)))
        val saved = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "Week", null).getOrThrow()

        mealPlanDao.softDelete(planId, 5L)

        assertThat(repository.getSavedPlan(saved.uuid)).isNotNull()
    }

    @Test
    fun `observeSavedPlans lists only the user's plans, newest first`() = runTest {
        val planId = givenPlan(days = listOf(day(0)))
        val first = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "First", null).getOrThrow()
        savedDao.upsert(
            requireNotNull(savedDao.getById(first.uuid)).copy(createdAt = 1L)
        )
        val second = repository.saveFromMealPlan(UUID.randomUUID(), userId, planId, "Second", null).getOrThrow()
        repository.saveFromMealPlan(UUID.randomUUID(), UUID.randomUUID(), planId, "Other user", null)

        repository.observeSavedPlans(userId).test {
            assertThat(awaitItem().map { it.uuid }).containsExactly(second.uuid, first.uuid).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- helpers ---

    private suspend fun givenPlan(
        days: List<MealPlanDayEntity>,
        preferences: MealPlanPreferences = preferences(planLengthDays = days.size),
    ): UUID {
        val planId = UUID.randomUUID()
        mealPlanDao.upsertMealPlan(
            MealPlanEntity(
                uuid = planId,
                userId = userId,
                name = "Dated plan",
                status = "READY",
                preferencesJson = preferences.toJson(),
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            )
        )
        mealPlanDao.upsertDays(days.map { it.copy(mealPlanId = planId) })
        return planId
    }

    private fun day(dayIndex: Int, cooked: Boolean = false) = MealPlanDayEntity(
        uuid = UUID.randomUUID(),
        mealPlanId = UUID.randomUUID(),
        dayIndex = dayIndex,
        dinnerRecipeId = UUID.randomUUID(),
        lunchRecipeId = UUID.randomUUID(),
        dinnerCookedAt = if (cooked) 99L else null,
        lunchCookedAt = if (cooked) 99L else null,
    )

    private fun preferences(planLengthDays: Int, servings: Int = 2) = MealPlanPreferences(
        planLengthDays = planLengthDays,
        mealType = MealType.DINNER_AND_LUNCH,
        dietaryRestrictions = setOf(DietaryRestriction.NONE),
        recipeSource = RecipeSource.INCLUDE_PUBLIC,
        maxPrepTimeMinutes = null,
        servingsPerMeal = servings,
        batchCooking = false,
        leftoverFriendly = false,
        varietyPreference = VarietyPreference.MEDIUM,
    )
}
