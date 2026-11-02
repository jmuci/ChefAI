package com.tenmilelabs.chefai.mealplans.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import org.junit.Test
import java.time.LocalDate

class AddMealRequestTest {

    private val handle = SavedStateHandle()
    private val date: LocalDate = LocalDate.of(2026, 11, 13)

    @Test
    fun `take returns the request begun, once`() {
        AddMealRequest.begin(handle, date, MealSlot.LUNCH)

        assertThat(AddMealRequest.take(handle)).isEqualTo(AddMealRequest.Pending(date, MealSlot.LUNCH))
        // Consumed: nothing is left to fire on the next recipe saved elsewhere.
        assertThat(AddMealRequest.take(handle)).isNull()
    }

    @Test
    fun `take with no request waiting returns null`() {
        assertThat(AddMealRequest.take(handle)).isNull()
    }

    @Test
    fun `take drops a request that no longer parses`() {
        handle[AddMealRequest.KEY_DATE] = "not a date"
        handle[AddMealRequest.KEY_SLOT] = MealSlot.DINNER.name

        assertThat(AddMealRequest.take(handle)).isNull()
        assertThat(handle.get<String>(AddMealRequest.KEY_SLOT)).isNull()
    }

    @Test
    fun `cancelling drops the request`() {
        AddMealRequest.begin(handle, date, MealSlot.DINNER)

        AddMealRequest.cancel(handle)

        assertThat(AddMealRequest.take(handle)).isNull()
    }
}
