package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

/** A supermarket aisle, in the order you walk them. Declaration order is display order. */
enum class GrocerySection(val label: String, val emoji: String) {
    PRODUCE("Fresh produce", "🥬"),
    BAKERY("Bakery", "🥖"),
    MEAT_AND_SEAFOOD("Meat & seafood", "🥩"),
    DAIRY_AND_EGGS("Dairy & eggs", "🥛"),
    FROZEN("Frozen", "🧊"),
    PANTRY("Pantry & dry goods", "🥫"),
    SPICES_AND_BAKING("Spices & baking", "🧂"),
    BEVERAGES("Drinks", "🧃"),
    OTHER("Other", "🛒"),
}
