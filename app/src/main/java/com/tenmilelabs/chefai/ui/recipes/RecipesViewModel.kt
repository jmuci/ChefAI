package com.tenmilelabs.chefai.ui.recipes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tenmilelabs.chefai.data.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository
) : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is recipes Fragment"
    }

/*    private val _recipes = recipesRepository.getRecipesObservable().onCompletion {
        recipes -> Log.d("RecipesViewModel", "Loaded ${recipes} Recipes from the DB ")
    }*/
    //var accessedDatabase: SupportSQLiteDatabase? = db.getOpenHelper().getWritableDatabase()

    val text: LiveData<String> = _text
}