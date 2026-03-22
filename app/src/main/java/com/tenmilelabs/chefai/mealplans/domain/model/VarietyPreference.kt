package com.tenmilelabs.chefai.mealplans.domain.model

enum class VarietyPreference(val emoji: String, val label: String) {
    HIGH("\uD83C\uDFA8", "Maximum variety"),
    MEDIUM("\u2696\uFE0F", "Balanced"),
    LOW("\u267B\uFE0F", "Repeat favorites"),
}
