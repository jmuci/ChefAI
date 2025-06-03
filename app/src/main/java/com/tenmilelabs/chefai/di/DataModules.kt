package com.tenmilelabs.chefai.di

import android.content.Context
import androidx.room.Room
import com.tenmilelabs.chefai.data.source.local.ChefAIDataBase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModules {

    @Singleton
    fun provideDataBase(@ApplicationContext context: Context): ChefAIDataBase {
        return Room.databaseBuilder(
            context,
            ChefAIDataBase::class.java,
            "Recipes.db"
        ).build()
    }

    @Provides
    fun provideRecipeDao(database: ChefAIDataBase) = database.recipeDao()
}