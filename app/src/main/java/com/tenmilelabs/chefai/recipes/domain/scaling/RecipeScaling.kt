package com.tenmilelabs.chefai.recipes.domain.scaling

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient

/**
 * Scales a recipe's ingredient quantities to a chosen number of portions.
 *
 * Pure and Android-free so the arithmetic can be unit-tested directly; the ViewModel only decides
 * *when* to apply it. Nothing here is persisted — the chosen portion count is a way of reading the
 * recipe, not an edit to it.
 *
 * Only quantities scale. Step text is left exactly as written (an instruction saying "add 200g of
 * flour" keeps saying that), and prep/cook times are untouched because they don't scale linearly.
 */
object RecipeScaling {

    /**
     * Assumed yield for a recipe that never published one. `servings = 0` is a real value in this
     * database: [com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraft] writes it for pages
     * with no `recipeYield`, and `NetworkRecipe.toDomain` hardcodes it.
     */
    const val DEFAULT_SERVINGS = 4

    const val MIN_SERVINGS = 1

    /** Upper bound for an ordinary recipe. A recipe that yields more than this raises its own — see [servingsRange]. */
    const val MAX_SERVINGS = 10

    /** The recipe's own yield, or [DEFAULT_SERVINGS] when it doesn't have a usable one. */
    fun baseServings(recipeServings: Int): Int =
        if (recipeServings >= MIN_SERVINGS) recipeServings else DEFAULT_SERVINGS

    /**
     * Selectable portion counts. [MAX_SERVINGS] normally, but a batch recipe that already yields
     * more than that (24 cookies) raises the ceiling to its own yield, so its starting point is
     * always inside the range and the user can always get back to the recipe as written.
     */
    fun servingsRange(baseServings: Int): IntRange = MIN_SERVINGS..maxOf(MAX_SERVINGS, baseServings)

    /**
     * [ingredients] with every quantity multiplied by `target / base`.
     *
     * Returns the list untouched when there is nothing to do, so the common case (viewing a recipe
     * at its own yield) allocates nothing and the equality checks in Compose stay cheap.
     */
    fun scale(
        ingredients: List<RecipeIngredient>,
        baseServings: Int,
        targetServings: Int,
    ): List<RecipeIngredient> {
        if (baseServings <= 0 || targetServings <= 0 || baseServings == targetServings) return ingredients
        val factor = targetServings.toDouble() / baseServings
        return ingredients.map { it.copy(quantity = it.quantity * factor) }
    }
}
