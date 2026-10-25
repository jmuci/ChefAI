package com.tenmilelabs.chefai.mealplans.domain.week

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** How many days a week holds. Named because it is also the navigator's step size. */
const val DAYS_IN_WEEK: Int = 7

/**
 * One planned meal, located on a calendar date rather than inside a plan.
 *
 * @property planName the owning plan's name, so a slot two plans both fill can say which is which.
 * @property recipe `null` when the device has the plan but not yet the recipe it points at — an
 *   anonymous session that has never pulled, most often. The slot is still planned, so it must not
 *   render as an empty day; the UI shows a placeholder instead.
 * @property isCooked this plan's own mark for its own (day, slot) — never another plan's.
 */
data class PlannedWeekMeal(
    val planId: UUID,
    val planName: String,
    val dayId: UUID,
    val slot: MealSlot,
    val recipeId: UUID,
    val recipe: RecipePreview?,
    val isCooked: Boolean,
)

/** One row of the week: a date, and whatever is planned on it. */
data class WeekDay(
    val date: LocalDate,
    /**
     * Empty means nothing is planned — the design's "+ Add meal" row. Ordered by slot, then by
     * plan; a slot that overlapping plans both fill appears once per plan (ADR-015 amendment).
     */
    val meals: List<PlannedWeekMeal>,
) {
    /** Slots on this date that more than one plan fills — the meals that need a plan label. */
    val sharedSlots: Set<MealSlot>
        get() = meals.groupingBy { it.slot }.eachCount().filterValues { it > 1 }.keys
}

/**
 * Seven consecutive days and the meals that land on them.
 *
 * @property planIds every plan contributing a meal to this week. Its size is what decides whether
 *   "Generate Grocery List" has a single plan to open — see [singlePlanId].
 */
data class MealPlanWeek(
    val weekStart: LocalDate,
    val days: List<WeekDay>,
    val planIds: Set<UUID>,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays((DAYS_IN_WEEK - 1).toLong())

    val mealCount: Int get() = days.sumOf { it.meals.size }

    /**
     * The one plan this week is made of, or `null` when it spans none or several.
     *
     * The shopping list is routed by a plan id and aggregates for one plan's servings, so a week
     * that is not exactly one plan has no existing list to open. ADR-015 Decision 6.
     */
    val singlePlanId: UUID? get() = planIds.singleOrNull()

    /** Recipe ids planned this week, deduplicated — what the grocery aggregation shops for. */
    val recipeIds: List<UUID>
        get() = days.flatMap { day -> day.meals.map { it.recipeId } }.distinct()

    /** How many slots each recipe fills, for `ShoppingListBuilder.slotCountByRecipe`. */
    val slotCountByRecipe: Map<UUID, Int>
        get() = days.flatMap { day -> day.meals.map { it.recipeId } }
            .groupingBy { it }
            .eachCount()
}

/**
 * Projects plans onto a week of calendar dates.
 *
 * Pure and Android-free so the projection rules can be unit-tested directly; the ViewModel only
 * feeds it flows. Mirrors how `ShoppingListBuilder` relates to `ShoppingListViewModel`.
 *
 * The screen it backs is date-first, not plan-first: it does not pick a plan and render it, it
 * picks seven dates and asks every plan which of its days land there. See ADR-015 Decision 1.
 */
object MealPlanWeekProjector {

    /**
     * @param plans every plan in scope. [MealPlanStatus.ARCHIVED] ones are dropped here rather than
     *   by the caller, so no caller can forget.
     * @param recipes previews keyed by recipe id; a miss leaves [PlannedWeekMeal.recipe] null.
     * @param zone the zone the plan's `createdAt` instant is read in — the device's, via the
     *   injected clock, so a test can pin it.
     */
    fun project(
        plans: List<MealPlan>,
        weekStart: LocalDate,
        recipes: Map<UUID, RecipePreview>,
        zone: ZoneId,
    ): MealPlanWeek {
        val dates = (0 until DAYS_IN_WEEK).map { weekStart.plusDays(it.toLong()) }
        val window = dates.toSet()

        // Every meal any live plan puts inside the window.
        val candidates = plans
            .filter { it.status != MealPlanStatus.ARCHIVED }
            .flatMap { plan ->
                plan.days.flatMap { day ->
                    val date = planDateFor(plan.createdAt, day.dayIndex, zone)
                    if (date !in window) return@flatMap emptyList()
                    MealSlot.entries.mapNotNull { slot ->
                        val recipeId = day.recipeIdFor(slot) ?: return@mapNotNull null
                        Candidate(
                            date = date,
                            plan = plan,
                            meal = PlannedWeekMeal(
                                planId = plan.uuid,
                                planName = plan.name,
                                dayId = day.uuid,
                                slot = slot,
                                recipeId = recipeId,
                                recipe = recipes[recipeId],
                                isCooked = day.cookedAtFor(slot) != null,
                            ),
                        )
                    }
                }
            }

        // Overlapping plans are not resolved to a single winner: every planned meal is shown, and
        // two plans filling one slot both appear under it. Picking one silently hid the other
        // plan's meal *and* its cooked mark (ADR-015 amendment, superseding Decision 3).
        val mealsByDate = candidates
            .sortedWith(compareBy<Candidate> { it.meal.slot.ordinal }.then(PLAN_ORDER))
            .groupBy(keySelector = { it.date }, valueTransform = { it.meal })

        val days = dates.map { date ->
            WeekDay(date = date, meals = mealsByDate[date].orEmpty())
        }

        return MealPlanWeek(
            weekStart = weekStart,
            days = days,
            planIds = days.flatMap { day -> day.meals.map { it.planId } }.toSet(),
        )
    }

    private data class Candidate(
        val date: LocalDate,
        val plan: MealPlan,
        val meal: PlannedWeekMeal,
    )

    /**
     * Within one slot, older plan first. Deliberately not `updatedAt`: regenerating or editing a
     * plan bumps it, and rows that reshuffle after an unrelated edit read as something having moved.
     * `uuid` makes the order total when two plans share a creation instant.
     */
    private val PLAN_ORDER: Comparator<Candidate> = compareBy(
        { it.plan.createdAt },
        { it.plan.uuid },
    )
}

/**
 * The calendar date a plan's [dayIndex] lands on: day 0 is the date the plan was created.
 *
 * `MealPlan` carries no start date, so this assumption *is* the schedule — see ADR-015 Decision 2,
 * including what it costs when a plan is created mid-week. Duplicated deliberately from
 * `MealPlanBoard.mealPlanDateFor`, which is the same two lines in the UI layer; domain cannot
 * import from `ui/`, and the pair should collapse into this one when a `startDate` column lands.
 */
fun planDateFor(planCreatedAt: Long, dayIndex: Int, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(planCreatedAt).atZone(zone).toLocalDate().plusDays(dayIndex.toLong())

/**
 * The start of the week [date] falls in.
 *
 * [firstDayOfWeek] comes from the device locale rather than a hard Monday: the handoff's Mon–Sun is
 * one locale's rendering of "a week", and a planner that disagrees with the phone's own calendar
 * about where the week begins is wrong everywhere but there.
 */
fun weekStartFor(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
    val shift = (date.dayOfWeek.value - firstDayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
    return date.minusDays(shift.toLong())
}
