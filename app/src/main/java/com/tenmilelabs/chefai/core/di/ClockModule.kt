package com.tenmilelabs.chefai.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * The device clock, injected rather than read statically.
 *
 * Anything that asks "what day is it?" is otherwise untestable except on the day it was written —
 * the Meal Plans week view opens on the current week and draws today's row in accent, and a test
 * of either that called `LocalDate.now()` would only pass on the right weekday. Injecting the
 * clock lets a test pin the date, the same seam `SessionManager.uuidGenerator` and
 * `RecipeTimerController.timeSource` already provide for their own non-determinism.
 *
 * `systemDefaultZone()` rather than UTC on purpose: a meal is planned in the zone the kitchen is
 * in, and the clock carries the zone every date calculation downstream reads.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Singleton
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
