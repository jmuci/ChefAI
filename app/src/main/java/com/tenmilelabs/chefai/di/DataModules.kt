package com.tenmilelabs.chefai.di

import android.content.Context
import androidx.room.Room
import com.tenmilelabs.chefai.data.repository.DefaultRecipeRepository
import com.tenmilelabs.chefai.data.repository.IngredientsRepositoryImpl
import com.tenmilelabs.chefai.data.repository.LabelsRepositoryImpl
import com.tenmilelabs.chefai.data.repository.TagsRepositoryImpl
import com.tenmilelabs.chefai.data.source.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.domain.repository.IngredientsRepository
import com.tenmilelabs.chefai.domain.repository.LabelsRepository
import com.tenmilelabs.chefai.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.domain.repository.TagsRepository
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
    abstract fun bindIngredientsRepository(repository: IngredientsRepositoryImpl): IngredientsRepository

    @Singleton
    @Binds
    abstract fun bindLabelsRepository(repository: LabelsRepositoryImpl): LabelsRepository

    @Singleton
    @Binds
    abstract fun bindTagsRepository(repository: TagsRepositoryImpl): TagsRepository
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
    fun provideIngredientDao(database: ChefAIDataBase) = database.ingredientDao()

    @Provides
    fun provideLabelDao(database: ChefAIDataBase) = database.labelDao()

    @Provides
    fun provideTagDao(database: ChefAIDataBase) = database.tagDao()
}