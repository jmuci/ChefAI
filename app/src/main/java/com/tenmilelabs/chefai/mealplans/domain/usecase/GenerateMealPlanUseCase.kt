package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.sync.SyncExecutor
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Fills a saved meal plan with a schedule, picking the generation path by session type and
 * [MealPlanPreferences.recipeSource], with [LocalMealPlanGenerator] as the universal fallback:
 * - Authenticated: the existing server round trip (push -> generate -> poll -> pull).
 * - Anonymous + [RecipeSource.INCLUDE_PUBLIC]: the anonymous-capable stateless endpoint — this is
 *   what closes the gap between a device's small local catalog and the server's full public
 *   catalog for a signed-out user.
 * - Anonymous + [RecipeSource.COLLECTION_ONLY]: local only, no network call at all — "my
 *   collection" exists only on this device, so the server can't answer that query and isn't asked.
 *
 * The single home for this routing rule: [com.tenmilelabs.chefai.mealplans.ui.create.CreateMealPlanViewModel]
 * and [com.tenmilelabs.chefai.mealplans.ui.detail.MealPlanDetailViewModel] both call this instead
 * of each re-implementing the branching, so the two screens can't drift on when a plan goes to the
 * server versus the device.
 *
 * Re-reads the plan from Room immediately before the local fallback rather than reusing a
 * pre-generation snapshot: the primary path's own sync/pull can have mutated the row first (e.g. a
 * newer edit on another device landing via last-writer-wins), and the fallback should schedule
 * against whatever preferences are current, not stale ones read before that sync ran.
 */
class GenerateMealPlanUseCase @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val sessionManager: SessionManager,
    private val syncExecutor: SyncExecutor,
    private val localMealPlanGenerator: LocalMealPlanGenerator,
) {

    /** @return whether [planId] ended up with a schedule, by any path. */
    suspend operator fun invoke(planId: UUID, preferences: MealPlanPreferences): Boolean {
        val isAnonymous = sessionManager.userSession.value is UserSession.Anonymous
        val filled = when {
            isAnonymous && preferences.recipeSource == RecipeSource.COLLECTION_ONLY -> false
            isAnonymous -> generateStateless(planId, preferences)
            else -> generateRemotely(planId)
        }
        if (filled) return true

        val plan = mealPlanRepository.observeMealPlan(planId).first() ?: run {
            Timber.w("GenerateMealPlanUseCase: plan $planId not found for local fallback")
            return false
        }
        return localMealPlanGenerator(plan).isSuccess
    }

    /** Runs the server round trip for [planId]. @return whether it produced a schedule. */
    private suspend fun generateRemotely(planId: UUID): Boolean = try {
        Timber.d("generateRemotely: pushing meal plan $planId")
        syncExecutor.sync()

        Timber.d("generateRemotely: calling generate API for $planId")
        mealPlanRepository.requestGeneration(planId).getOrThrow()

        delay(GENERATION_POLL_DELAY_MS)
        Timber.d("generateRemotely: pulling generated results for $planId")
        syncExecutor.sync()

        mealPlanRepository.observeMealPlan(planId).first()?.days?.isNotEmpty() == true
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.w(e, "generateRemotely: failed for $planId")
        false
    }

    /**
     * Runs the anonymous-capable stateless endpoint for [planId]. No sync/poll dance: unlike
     * [generateRemotely] there is no plan row on the server to push first or pull a result from.
     */
    private suspend fun generateStateless(planId: UUID, preferences: MealPlanPreferences): Boolean = try {
        Timber.d("generateStateless: calling stateless generate for $planId (anonymous session)")
        val days = mealPlanRepository.generateStatelessAndSave(planId, preferences).getOrThrow()
        days > 0
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.w(e, "generateStateless: failed for $planId")
        false
    }

    companion object {
        /** Delay before pulling to let server-side generation finish. */
        private const val GENERATION_POLL_DELAY_MS = 2_000L
    }
}
