package com.tenmilelabs.chefai.mealplans.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class AddMealRequestTest {

    private val handle = SavedStateHandle()
    private val date: LocalDate = LocalDate.of(2026, 11, 13)

    @Test
    fun `a recipe delivered after begin is emitted once with its date and slot`() = runTest {
        val recipeId = UUID.randomUUID()
        AddMealRequest.begin(handle, date, MealSlot.LUNCH)

        AddMealRequest.results(handle).test {
            assertThat(AddMealRequest.deliver(handle, recipeId)).isTrue()

            assertThat(awaitItem()).isEqualTo(AddMealRequest.Delivered(date, MealSlot.LUNCH, recipeId))
            // Consumed: nothing is left to fire on the next recipe saved elsewhere.
            assertThat(handle.get<String>(AddMealRequest.KEY_DATE)).isNull()
            assertThat(handle.get<String>(AddMealRequest.KEY_RECIPE)).isNull()
            expectNoEvents()
        }
    }

    @Test
    fun `a delivery that lands before anyone collects is still emitted`() = runTest {
        val recipeId = UUID.randomUUID()
        AddMealRequest.begin(handle, date, MealSlot.DINNER)
        AddMealRequest.deliver(handle, recipeId)

        AddMealRequest.results(handle).test {
            assertThat(awaitItem().recipeId).isEqualTo(recipeId)
        }
    }

    @Test
    fun `deliver with no pending request is refused`() {
        assertThat(AddMealRequest.deliver(handle, UUID.randomUUID())).isFalse()
        assertThat(AddMealRequest.deliver(null, UUID.randomUUID())).isFalse()
        assertThat(handle.get<String>(AddMealRequest.KEY_RECIPE)).isNull()
    }

    @Test
    fun `returning with nothing delivered drops the request`() {
        AddMealRequest.begin(handle, date, MealSlot.DINNER)

        AddMealRequest.dropIfUndelivered(handle)

        assertThat(handle.get<String>(AddMealRequest.KEY_DATE)).isNull()
        assertThat(handle.get<String>(AddMealRequest.KEY_SLOT)).isNull()
        assertThat(AddMealRequest.deliver(handle, UUID.randomUUID())).isFalse()
    }

    @Test
    fun `returning with a recipe delivered keeps it for the collector`() {
        val recipeId = UUID.randomUUID()
        AddMealRequest.begin(handle, date, MealSlot.DINNER)
        AddMealRequest.deliver(handle, recipeId)

        AddMealRequest.dropIfUndelivered(handle)

        assertThat(handle.get<String>(AddMealRequest.KEY_RECIPE)).isEqualTo(recipeId.toString())
    }
}
