package com.tenmilelabs.chefai.di

import android.content.Context
import androidx.room.Room
import com.tenmilelabs.chefai.data.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.RecipesRepository
import com.tenmilelabs.chefai.data.source.local.ChefAIDataBase
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
            "Recipes.db"
        ).createFromAsset("database/ChefAI.sql").build()
    }

    @Provides
    fun provideRecipeDao(database: ChefAIDataBase) = database.recipeDao()
}