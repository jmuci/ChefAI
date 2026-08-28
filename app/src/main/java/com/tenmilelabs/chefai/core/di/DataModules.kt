package com.tenmilelabs.chefai.core.di

import android.content.Context
import androidx.room.Room
import com.tenmilelabs.chefai.auth.data.local.SecurePreferences
import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.collections.data.repository.DefaultCollectionsRepository
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.mealplans.data.network.MealPlanApiService
import com.tenmilelabs.chefai.mealplans.data.network.MealPlanNetworkDataSource
import com.tenmilelabs.chefai.mealplans.data.repository.DefaultMealPlanRepository
import com.tenmilelabs.chefai.mealplans.data.repository.DefaultShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.core.data.local.room.RoomTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_1_2
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_2_3
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_3_4
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_4_5
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_5_6
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_6_7
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_7_8
import com.tenmilelabs.chefai.core.data.repository.DefaultMetadataRepository
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter
import com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.recipes.data.network.SystemHostResolver
import com.tenmilelabs.chefai.recipes.data.network.WebViewHtmlFetcher
import com.tenmilelabs.chefai.recipes.data.network.WebViewImageFetcher
import com.tenmilelabs.chefai.recipes.domain.repository.HostResolver
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import com.tenmilelabs.chefai.recipes.domain.repository.RenderedHtmlFetcher
import com.tenmilelabs.chefai.recipes.domain.repository.RenderedImageFetcher
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.search.data.repository.DefaultRecipeSearchRepository
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Singleton
    @Binds
    abstract fun bindRecipeRepository(repository: DefaultRecipeRepository): RecipesRepository

    @Singleton
    @Binds
    abstract fun bindMetadataRepository(repository: DefaultMetadataRepository): MetadataRepository

    @Singleton
    @Binds
    abstract fun bindSecurePreferences(securePreferences: SecurePreferences): SecurePreferencesInterface

    @Singleton
    @Binds
    abstract fun bindCollectionsRepository(repository: DefaultCollectionsRepository): CollectionsRepository

    @Singleton
    @Binds
    abstract fun bindMealPlanRepository(repository: DefaultMealPlanRepository): MealPlanRepository

    @Singleton
    @Binds
    abstract fun bindShoppingListRepository(repository: DefaultShoppingListRepository): ShoppingListRepository

    @Singleton
    @Binds
    abstract fun bindMealPlanNetworkDataSource(service: MealPlanApiService): MealPlanNetworkDataSource

    @Singleton
    @Binds
    abstract fun bindRecipeImporter(importer: DefaultRecipeImporter): RecipeImporter

    @Singleton
    @Binds
    abstract fun bindRenderedHtmlFetcher(fetcher: WebViewHtmlFetcher): RenderedHtmlFetcher

    @Singleton
    @Binds
    abstract fun bindRenderedImageFetcher(fetcher: WebViewImageFetcher): RenderedImageFetcher

    @Singleton
    @Binds
    abstract fun bindRecipeSearchRepository(repository: DefaultRecipeSearchRepository): RecipeSearchRepository

    @Singleton
    @Binds
    abstract fun bindHostResolver(resolver: SystemHostResolver): HostResolver
}


@Module
@InstallIn(SingletonComponent::class)
object DatabaseModules {

    @Singleton
    @Provides
    fun provideDataBase(@ApplicationContext context: Context): ChefAIDataBase {
        return Room.databaseBuilder(
            context,
            ChefAIDataBase::class.java,
            "ChefAI.db"
        )
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8,
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideBookmarkedRecipeDao(database: ChefAIDataBase) = database.bookmarkedRecipeDao()

    @Provides
    fun provideRecipeDao(database: ChefAIDataBase) = database.recipeDao()

    @Provides
    fun provideRecipeStepDao(database: ChefAIDataBase) = database.recipeStepDao()

    @Provides
    fun provideRecipeIngredientDao(database: ChefAIDataBase) = database.recipeIngredientDao()

    @Provides
    fun provideIngredientDao(database: ChefAIDataBase) = database.ingredientDao()

    @Provides
    fun provideAllergenDao(database: ChefAIDataBase) = database.allergenDao()

    @Provides
    fun provideSourceClassificationDao(database: ChefAIDataBase) = database.sourceClassificationDao()

    @Provides
    fun provideLabelDao(database: ChefAIDataBase) = database.labelDao()

    @Provides
    fun provideRecipeLabelDao(database: ChefAIDataBase) = database.recipeLabelCrossRefDao()

    @Provides
    fun provideTagDao(database: ChefAIDataBase) = database.tagDao()

    @Provides
    fun provideRecipeTagDao(database: ChefAIDataBase) = database.recipeTagCrossRefDao()

    @Provides
    fun provideUserDao(database: ChefAIDataBase) = database.userDao()

    @Provides
    fun provideSyncMetadataDao(database: ChefAIDataBase) = database.syncMetadataDao()

    @Provides
    fun provideRecipeDraftDao(database: ChefAIDataBase) = database.recipeDraftDao()

    @Provides
    fun provideRecipeImageStateDao(database: ChefAIDataBase) = database.recipeImageStateDao()

    @Provides
    fun provideMealPlanDao(database: ChefAIDataBase) = database.mealPlanDao()

    @Provides
    fun provideShoppingListCheckDao(database: ChefAIDataBase) = database.shoppingListCheckDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(database: ChefAIDataBase): TransactionRunner =
        RoomTransactionRunner(database)
}
