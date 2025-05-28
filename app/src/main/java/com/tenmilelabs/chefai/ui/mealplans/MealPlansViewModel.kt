package com.tenmilelabs.chefai.ui.mealplans

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MealPlansViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is meal plans Fragment"
    }
    val text: LiveData<String> = _text
}