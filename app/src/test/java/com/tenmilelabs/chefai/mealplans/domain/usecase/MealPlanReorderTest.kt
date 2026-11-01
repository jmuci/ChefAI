package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class MealPlanReorderTest {

    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()
    private val d = UUID.randomUUID()

    private fun day(index: Int, dinner: UUID?, lunch: UUID? = null, dinnerCookedAt: Long? = null) =
        MealPlanDay(UUID.randomUUID(), index, dinnerRecipeId = dinner, lunchRecipeId = lunch, dinnerCookedAt = dinnerCookedAt)

    private fun dinner(day: MealPlanDay) = MealRef(day.uuid, MealSlot.DINNER)

    private fun List<MealPlanDay>.apply(changed: List<MealPlanDay>?): List<MealPlanDay> {
        val byId = changed.orEmpty().associateBy { it.uuid }
        return map { byId[it.uuid] ?: it }
    }

    @Test
    fun `moving down shifts the meals in between up`() {
        val days = listOf(day(0, a), day(1, b), day(2, c))

        val result = days.apply(MealPlanReorder.move(days, dinner(days[0]), dinner(days[2])))

        assertEquals(listOf(b, c, a), result.map { it.dinnerRecipeId })
    }

    @Test
    fun `moving up shifts the meals in between down`() {
        val days = listOf(day(0, a), day(1, b), day(2, c))

        val result = days.apply(MealPlanReorder.move(days, dinner(days[2]), dinner(days[0])))

        assertEquals(listOf(c, a, b), result.map { it.dinnerRecipeId })
    }

    @Test
    fun `cooked meals stay pinned and are skipped over`() {
        val days = listOf(day(0, a), day(1, b, dinnerCookedAt = 1L), day(2, c))

        val result = days.apply(MealPlanReorder.move(days, dinner(days[0]), dinner(days[2])))

        assertEquals(listOf(c, b, a), result.map { it.dinnerRecipeId })
        assertEquals(1L, result[1].dinnerCookedAt)
    }

    @Test
    fun `lunch and dinner slots are reordered in day-then-slot order`() {
        val days = listOf(day(0, dinner = b, lunch = a), day(1, dinner = d, lunch = c))

        val result = days.apply(
            MealPlanReorder.move(days, MealRef(days[0].uuid, MealSlot.LUNCH), MealRef(days[1].uuid, MealSlot.LUNCH)),
        )

        // Order was a, b, c, d; a moves to c's place -> b, c, a, d.
        assertEquals(listOf(b, c), listOf(result[0].lunchRecipeId, result[0].dinnerRecipeId))
        assertEquals(listOf(a, d), listOf(result[1].lunchRecipeId, result[1].dinnerRecipeId))
    }

    @Test
    fun `moving onto itself, a cooked meal or an unknown ref changes nothing`() {
        val days = listOf(day(0, a), day(1, b, dinnerCookedAt = 1L))

        assertNull(MealPlanReorder.move(days, dinner(days[0]), dinner(days[0])))
        assertNull(MealPlanReorder.move(days, dinner(days[0]), dinner(days[1])))
        assertNull(MealPlanReorder.move(days, MealRef(UUID.randomUUID(), MealSlot.DINNER), dinner(days[0])))
    }
}
