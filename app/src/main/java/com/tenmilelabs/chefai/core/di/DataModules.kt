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
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.core.data.local.room.RoomTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.repository.DefaultMetadataRepository
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter
import com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
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
    abstract fun bindMealPlanNetworkDataSource(service: MealPlanApiService): MealPlanNetworkDataSource

    @Singleton
    @Binds
    abstract fun bindRecipeImporter(importer: DefaultRecipeImporter): RecipeImporter
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
    fun provideMealPlanDao(database: ChefAIDataBase) = database.mealPlanDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(database: ChefAIDataBase): TransactionRunner =
        RoomTransactionRunner(database)
}
