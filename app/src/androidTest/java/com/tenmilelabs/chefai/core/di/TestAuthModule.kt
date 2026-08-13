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
import com.tenmilelabs.chefai.mealplans.data.network.MealPlanApiService
import com.tenmilelabs.chefai.mealplans.data.network.MealPlanNetworkDataSource
import com.tenmilelabs.chefai.mealplans.data.repository.DefaultMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.recipes.data.network.WebViewImageFetcher
import com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter
import com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipeImporter
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RenderedImageFetcher
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

        /**
         * Provides a singleton instance of FakeRecipeImporter — avoids real network calls to
         * third-party recipe sites in instrumented tests.
         */
        @Provides
        @Singleton
        fun provideFakeRecipeImporter(): FakeRecipeImporter {
            return FakeRecipeImporter()
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

    /**
     * Binds the meal plan network data source (same as production) — required for Hilt's shared
     * test component to resolve MealPlansViewModel's graph, even though this DAO test never uses it.
     * See docs/claude/gotchas.md #21.
     */
    @Binds
    @Singleton
    abstract fun bindMealPlanNetworkDataSource(service: MealPlanApiService): MealPlanNetworkDataSource

    /**
     * Binds the fake recipe importer — real fetch/parse is exercised by unit tests
     * ([com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporterTest] with Ktor
     * `MockEngine`); instrumented tests only need to exercise the UI/navigation/persistence wiring.
     */
    @Binds
    @Singleton
    abstract fun bindRecipeImporter(fake: FakeRecipeImporter): RecipeImporter

    /**
     * Binds the real [WebViewImageFetcher] (same as production) — required for Hilt's shared test
     * component to resolve `SaveImportedDraft`'s graph (see docs/claude/gotchas.md #21), even though
     * every instrumented test's fake-importer drafts use a blank `imageUrl`, so `CacheRecipeImage`
     * never actually reaches it at runtime.
     */
    @Binds
    @Singleton
    abstract fun bindRenderedImageFetcher(fetcher: WebViewImageFetcher): RenderedImageFetcher
}
