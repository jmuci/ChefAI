package com.tenmilelabs.chefai.core.di

import android.content.Context
import androidx.room.Room
import com.tenmilelabs.chefai.core.data.local.room.RoomTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [DatabaseModules] in instrumented tests with an in-memory Room database.
 * This ensures:
 * - Tests start with a clean, empty database on every run.
 * - No dependency on the production asset database file.
 * - No state pollution between test runs.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModules::class]
)
object TestDatabaseModule {

    @Singleton
    @Provides
    fun provideInMemoryDatabase(@ApplicationContext context: Context): ChefAIDataBase {
        return Room.inMemoryDatabaseBuilder(context, ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
    }

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
    @Singleton
    fun provideTransactionRunner(database: ChefAIDataBase): TransactionRunner =
        RoomTransactionRunner(database)
}
