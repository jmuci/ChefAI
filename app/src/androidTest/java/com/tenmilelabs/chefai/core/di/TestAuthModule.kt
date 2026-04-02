package com.tenmilelabs.chefai.core.di

import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.auth.data.network.AuthNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import com.tenmilelabs.chefai.collections.data.repository.DefaultCollectionsRepository
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.data.repository.DefaultMetadataRepository
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.mealplans.data.repository.DefaultMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test module that replaces [AuthModule] for instrumented tests.
 * Provides fake implementations of authentication dependencies.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthModule::class]
)
abstract class TestAuthModule {

    companion object {
        /**
         * Provides a singleton instance of FakeAuthNetworkDataSource.
         */
        @Provides
        @Singleton
        fun provideFakeAuthNetworkDataSource(): FakeAuthNetworkDataSource {
            return FakeAuthNetworkDataSource()
        }

        @Provides
        fun bindAuthNetworkDataSource(
            fake: FakeAuthNetworkDataSource
        ): AuthNetworkDataSource = fake
    }

    /**
     * Binds SessionManager as the implementation of TokenProvider.
     */
    @Binds
    @Singleton
    abstract fun bindTokenProvider(sessionManager: SessionManager): TokenProvider
}

/**
 * Test module that provides fake SecurePreferences and other repository bindings.
 * Replaces RepositoryModule from the production code.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class]
)
abstract class TestRepositoryModule {

    companion object {
        /**
         * Provides a singleton instance of FakeSecurePreferences.
         */
        @Provides
        @Singleton
        fun provideSecurePreferences(): SecurePreferencesInterface {
            return FakeSecurePreferences()
        }
    }

    /**
     * Binds the recipe repository (same as production).
     */
    @Binds
    @Singleton
    abstract fun bindRecipeRepository(repository: DefaultRecipeRepository): RecipesRepository

    /**
     * Binds the metadata repository (same as production).
     */
    @Binds
    @Singleton
    abstract fun bindMetadataRepository(repository: DefaultMetadataRepository): MetadataRepository

    /**
     * Binds the collections repository (same as production).
     */
    @Binds
    @Singleton
    abstract fun bindCollectionsRepository(repository: DefaultCollectionsRepository): CollectionsRepository

    /**
     * Binds the meal plan repository (same as production).
     */
    @Binds
    @Singleton
    abstract fun bindMealPlanRepository(repository: DefaultMealPlanRepository): MealPlanRepository
}
