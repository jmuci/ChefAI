package com.tenmilelabs.chefai.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dagger Hilt module for providing authentication-related dependencies.
 *
 * Note: ApplicationScope and its CoroutineScope provider are defined in CoroutinesModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    // Authentication-related providers can be added here if needed in the future
}
