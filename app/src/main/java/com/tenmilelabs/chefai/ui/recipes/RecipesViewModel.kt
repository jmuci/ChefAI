package com.tenmilelabs.chefai.ui.recipes

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tenmilelabs.chefai.data.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject

@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository
) : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is recipes Fragment"
    }

    private val _recipes = recipesRepository.getRecipesObservable().onCompletion {
        recipes -> Log.d("RecipesViewModel", "Loaded ${recipes} Recipes from the DB ")
    }
    val text: LiveData<String> = _text
}