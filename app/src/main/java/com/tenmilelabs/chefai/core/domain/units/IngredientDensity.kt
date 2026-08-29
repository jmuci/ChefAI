package com.tenmilelabs.chefai.core.domain.units

/**
 * Typical densities for the pantry staples where a cup-to-gram conversion is worth making, and for
 * nothing else.
 *
 * Volume and weight are different dimensions: crossing between them needs to know what is in the
 * cup, and the answer is only ever approximate. Published charts disagree with each other by around
 * 15% on plain flour alone, and scoop-versus-spoon technique moves a cup of it by another 30–40 g —
 * more than the disagreement between the charts. So this table is deliberately small and
 * deliberately boring: staples measured by volume in American recipes, where a European reader
 * genuinely wants grams and where a wrong-by-a-tenth answer still beats no answer.
 *
 * Everything absent from it converts volume-to-volume instead (cups become millilitres), which is
 * exact and needs no ingredient knowledge. [UnitConversion] marks every density-derived amount as
 * approximate so the UI can say so.
 *
 * Figures are grams per **US customary cup** ([MeasurementUnit.CUP]), following King Arthur
 * Baking's ingredient weight chart where it lists the ingredient.
 */
object IngredientDensity {

    /**
     * Grams per millilitre for [ingredientName], or `null` when there is no honest answer.
     *
     * The table entry has to be what the ingredient *is*, not merely a word the name happens to
     * contain. "Sugar snap peas" are peas, "butter lettuce" is lettuce and "cream of tartar" is a
     * powdered acid — pricing any of those by the staple named inside them is wrong by a factor of
     * two, and wrong invisibly, since the result carries the same "≈" as a real match. So the key
     * must sit at the **end** of the name, where English puts the head noun, after trailing
     * qualifiers ("…, sifted", "… (softened)") are dropped. Longest match wins, so "almond flour"
     * beats "flour".
     *
     * Anything this rejects converts volume-to-volume instead, which is exact. Failing to weigh is
     * always the safer failure.
     */
    fun gramsPerMillilitre(ingredientName: String): Double? {
        val normalized = normalize(ingredientName)
        if (normalized.isBlank()) return null
        if (PREPARATION_WORDS.any { normalized.contains(it) }) return null

        val head = headNoun(ingredientName)
        val gramsPerCup = GRAMS_PER_CUP[head]
            ?: GRAMS_PER_CUP.keys
                .filter { head.endsWith(" $it") }
                .maxByOrNull { it.length }
                ?.let { GRAMS_PER_CUP.getValue(it) }
            ?: return null

        return gramsPerCup / MeasurementUnit.CUP.inBaseUnits
    }

    /** Lowercased, punctuation replaced by spaces, whitespace collapsed. */
    internal fun normalize(raw: String): String =
        raw.lowercase()
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * The name with trailing qualifiers removed and normalised — "All-Purpose Flour, sifted" and
     * "plain flour (sifted)" both become the head phrase the table is matched against.
     */
    private fun headNoun(raw: String): String =
        normalize(raw.substringBefore(',').substringBefore('('))

    /**
     * A name carrying one of these describes a cut, not a substance, and its packed density is
     * anybody's guess — a cup of chopped onion depends entirely on how hard it was pressed in.
     * These fall back to a volume-to-volume conversion rather than inventing a weight.
     */
    private val PREPARATION_WORDS = listOf(
        "chopped", "diced", "shredded", "sliced", "minced", "grated", "crushed", "cubed",
    )

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val WHITESPACE = Regex("\\s+")

    /** Grams per US customary cup. Keys are already in [normalize]d form. */
    private val GRAMS_PER_CUP: Map<String, Double> = buildMap {
        fun entry(gramsPerCup: Double, vararg names: String) {
            names.forEach { put(it, gramsPerCup) }
        }

        // Flours and dry baking goods
        entry(120.0, "flour", "plain flour", "all purpose flour", "bread flour", "cake flour", "self raising flour")
        entry(113.0, "whole wheat flour", "wholemeal flour")
        entry(96.0, "almond flour", "ground almonds")
        entry(113.0, "cornstarch", "cornflour", "corn starch")
        entry(85.0, "cocoa powder", "cacao powder")
        entry(138.0, "cornmeal", "polenta")
        entry(167.0, "semolina")
        entry(112.0, "milk powder", "powdered milk")
        entry(106.0, "breadcrumbs", "bread crumbs", "dried breadcrumbs")
        entry(50.0, "panko", "panko breadcrumbs")

        // Sugars and syrups
        entry(198.0, "sugar", "granulated sugar", "caster sugar", "castor sugar", "white sugar")
        entry(213.0, "brown sugar", "light brown sugar", "dark brown sugar", "muscovado sugar")
        entry(113.0, "powdered sugar", "icing sugar", "confectioners sugar")
        entry(340.0, "honey", "golden syrup", "corn syrup", "molasses", "treacle")
        entry(322.0, "maple syrup")

        // Fats and dairy
        entry(227.0, "butter", "unsalted butter", "salted butter", "margarine")
        // Cooking oils sit at about 0.91 g/ml; 236.588 ml x 0.91 = 216 g.
        entry(216.0, "oil", "olive oil", "vegetable oil", "sunflower oil", "canola oil", "rapeseed oil", "coconut oil", "sesame oil")
        entry(242.0, "milk", "whole milk", "skim milk", "semi skimmed milk", "almond milk", "oat milk", "soy milk")
        entry(227.0, "buttermilk", "yogurt", "yoghurt", "greek yogurt", "greek yoghurt", "sour cream", "cream cheese", "creme fraiche")
        entry(232.0, "cream", "heavy cream", "double cream", "single cream", "whipping cream")
        entry(237.0, "water", "boiling water", "cold water")

        // Grains, pulses and cereals
        entry(185.0, "rice", "white rice", "long grain rice", "basmati rice", "jasmine rice")
        entry(190.0, "brown rice")
        entry(200.0, "arborio rice", "risotto rice")
        entry(89.0, "oats", "rolled oats", "porridge oats", "quick oats")
        entry(173.0, "couscous")
        entry(177.0, "quinoa")
        entry(192.0, "lentils", "red lentils", "green lentils")

        // Condiments, seasonings and pantry
        entry(273.0, "salt", "table salt", "fine salt")
        entry(145.0, "kosher salt", "flaky salt", "sea salt flakes")
        entry(192.0, "baking powder")
        entry(220.0, "baking soda", "bicarbonate of soda")
        entry(256.0, "peanut butter", "almond butter", "tahini")
        entry(224.0, "mayonnaise", "mayo")
        entry(240.0, "ketchup", "tomato ketchup")
        entry(262.0, "tomato paste", "tomato puree")
        entry(255.0, "soy sauce", "soya sauce")
        entry(239.0, "vinegar", "white vinegar", "balsamic vinegar", "apple cider vinegar")
        entry(170.0, "chocolate chips", "chocolate chunks")
        entry(149.0, "raisins", "sultanas", "currants")
        entry(142.0, "almonds", "hazelnuts", "peanuts")
        entry(113.0, "walnuts", "pecans")
    }
}
