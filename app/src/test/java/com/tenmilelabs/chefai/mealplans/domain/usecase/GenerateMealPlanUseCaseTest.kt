package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AuthToken
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.sync.FakeSyncExecutor
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import com.tenmilelabs.chefai.core.testutil.recipePreview2
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GenerateMealPlanUseCaseTest {

    private lateinit var mealPlanRepository: FakeMealPlanRepository
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var syncExecutor: FakeSyncExecutor
    private lateinit var useCase: GenerateMealPlanUseCase

    private val testUserId = UUID.randomUUID()
    private val planId = UUID.randomUUID()

    @Before
    fun setup() {
        mealPlanRepository = FakeMealPlanRepository()
        recipesRepository = FakeRecipesRepository()
        recipesRepository.setRecipePreviewsToEmit(emptyList())
        sessionManager = mockk()
        syncExecutor = FakeSyncExecutor()
        useCase = GenerateMealPlanUseCase(
            mealPlanRepository = mealPlanRepository,
            sessionManager = sessionManager,
            syncExecutor = syncExecutor,
            localMealPlanGenerator = LocalMealPlanGenerator(recipesRepository, mealPlanRepository),
        )
    }

    private fun anonymousSession() {
        every { sessionManager.userSession } returns MutableStateFlow(UserSession.Anonymous(testUserId))
    }

    private fun authenticatedSession() {
        every { sessionManager.userSession } returns MutableStateFlow(
            UserSession.Authenticated(
                user = User(testUserId, "Test", "t@example.com", ""),
                authToken = AuthToken("token", "refresh", Long.MAX_VALUE),
            )
        )
    }

    private fun preferences(recipeSource: RecipeSource) = MealPlanPreferences(
        planLengthDays = 3,
        mealType = MealType.DINNER,
        dietaryRestrictions = setOf(DietaryRestriction.NONE),
        recipeSource = recipeSource,
        maxPrepTimeMinutes = null,
        servingsPerMeal = 2,
        batchCooking = false,
        leftoverFriendly = false,
        varietyPreference = VarietyPreference.MEDIUM,
    )

    private fun seedPlan(recipeSource: RecipeSource) {
        mealPlanRepository.emitPlans(
            MealPlan(
                uuid = planId,
                userId = testUserId,
                name = "Test plan",
                preferences = preferences(recipeSource),
                status = MealPlanStatus.DRAFT,
                createdAt = 0L,
                updatedAt = 0L,
                days = emptyList(),
            )
        )
    }

    @Test
    fun `authenticated session uses the server round trip, never the stateless endpoint`() = runTest {
        authenticatedSession()
        seedPlan(RecipeSource.INCLUDE_PUBLIC)
        mealPlanRepository.daysFromServer = listOf(day())

        val filled = useCase(planId, preferences(RecipeSource.INCLUDE_PUBLIC))

        assertThat(filled).isTrue()
        assertThat(mealPlanRepository.generationRequestedIds).containsExactly(planId)
        assertThat(mealPlanRepository.statelessGenerationRequestedIds).isEmpty()
    }

    @Test
    fun `anonymous plus INCLUDE_PUBLIC uses the stateless endpoint, never requestGeneration`() = runTest {
        anonymousSession()
        seedPlan(RecipeSource.INCLUDE_PUBLIC)
        mealPlanRepository.daysFromStatelessServer = listOf(day())

        val filled = useCase(planId, preferences(RecipeSource.INCLUDE_PUBLIC))

        assertThat(filled).isTrue()
        assertThat(mealPlanRepository.statelessGenerationRequestedIds).containsExactly(planId)
        assertThat(mealPlanRepository.generationRequestedIds).isEmpty()
        assertThat(syncExecutor.syncCount).isEqualTo(0)
    }

    @Test
    fun `anonymous plus COLLECTION_ONLY never touches the network`() = runTest {
        anonymousSession()
        seedPlan(RecipeSource.COLLECTION_ONLY)
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))

        val filled = useCase(planId, preferences(RecipeSource.COLLECTION_ONLY))

        assertThat(filled).isTrue()
        assertThat(mealPlanRepository.generationRequestedIds).isEmpty()
        assertThat(mealPlanRepository.statelessGenerationRequestedIds).isEmpty()
        assertThat(mealPlanRepository.locallyGeneratedIds).containsExactly(planId)
    }

    @Test
    fun `falls back to local generation when the primary path fails, and re-reads the plan for it`() = runTest {
        authenticatedSession()
        seedPlan(RecipeSource.INCLUDE_PUBLIC)
        mealPlanRepository.shouldFailGeneration = true
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))

        val filled = useCase(planId, preferences(RecipeSource.INCLUDE_PUBLIC))

        assertThat(filled).isTrue()
        assertThat(mealPlanRepository.locallyGeneratedIds).containsExactly(planId)
    }

    @Test
    fun `returns false when every path fails to fill the plan`() = runTest {
        authenticatedSession()
        seedPlan(RecipeSource.INCLUDE_PUBLIC)
        mealPlanRepository.shouldFailGeneration = true
        // No local recipes seeded — local fallback has nothing to draw from either.

        val filled = useCase(planId, preferences(RecipeSource.INCLUDE_PUBLIC))

        assertThat(filled).isFalse()
    }

    @Test
    fun `returns false without touching local generation when the plan does not exist`() = runTest {
        authenticatedSession()
        mealPlanRepository.shouldFailGeneration = true
        // Deliberately do not seed a plan for planId.

        val filled = useCase(planId, preferences(RecipeSource.INCLUDE_PUBLIC))

        assertThat(filled).isFalse()
        assertThat(mealPlanRepository.locallyGeneratedIds).isEmpty()
    }

    private fun day() = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = 0,
        dinnerRecipeId = UUID.randomUUID(),
        lunchRecipeId = null,
    )
}
