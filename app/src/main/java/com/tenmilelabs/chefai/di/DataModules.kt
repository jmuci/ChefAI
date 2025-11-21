package com.tenmilelabs.chefai.di

import android.content.Context
import androidx.room.Room
import com.tenmilelabs.chefai.data.repository.DefaultMetadataRepository
import com.tenmilelabs.chefai.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.source.local.SecurePreferences
import com.tenmilelabs.chefai.data.source.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.data.source.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.domain.repository.RecipesRepository
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
        ).createFromAsset("database/ChefAI.db").build()
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
    fun provideLabelDao(database: ChefAIDataBase) = database.labelDao()

    @Provides
    fun provideRecipeLabelDao(database: ChefAIDataBase) = database.recipeLabelCrossRefDao()

    @Provides
    fun provideTagDao(database: ChefAIDataBase) = database.tagDao()

    @Provides
    fun provideRecipeTagDao(database: ChefAIDataBase) = database.recipeTagCrossRefDao()
}