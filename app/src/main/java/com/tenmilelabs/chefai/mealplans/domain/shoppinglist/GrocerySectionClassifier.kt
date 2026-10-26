package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.BAKERY
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.BEVERAGES
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.DAIRY_AND_EGGS
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.FROZEN
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.MEAT_AND_SEAFOOD
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.OTHER
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.PANTRY
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.PRODUCE
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection.SPICES_AND_BAKING
import java.text.Normalizer

/**
 * Maps an ingredient's display name to the [GrocerySection] it is shopped in.
 *
 * Keyword-based rather than backed by a DB column: `ingredients.sourcePrimaryId` is `null` for
 * everything created locally (see `SidecarMapper.toIngredientEntity`), so it isn't a usable signal,
 * and adding a real column would mean a sync-payload field and backend agreement. `OTHER` is a
 * perfectly good answer for anything the table below doesn't recognise.
 */
object GrocerySectionClassifier {

    fun classify(ingredientName: String): GrocerySection {
        val normalized = normalize(ingredientName)
        if (normalized.isBlank()) return OTHER
        // Phrases match whole words only — a bare substring test lets "tin of" match inside
        // "martin of" — with an optional plural on the last word ("chicken breasts").
        val padded = " $normalized "
        fun hasPhrase(phrase: String) = PLURAL_ENDINGS.any { padded.contains(" $phrase$it ") }

        FROZEN_OVERRIDE_PHRASES.firstOrNull(::hasPhrase)?.let { return FROZEN }
        PANTRY_OVERRIDE_PHRASES.firstOrNull(::hasPhrase)?.let { return PANTRY }

        for (section in PHRASE_PRIORITY) {
            val phrases = phraseRules.getValue(section)
            if (phrases.any(::hasPhrase)) return section
        }

        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        for (section in TOKEN_PRIORITY) {
            val keywords = tokenRules.getValue(section)
            // Singular via "s" (eggs, patatas) or "es" (tomatoes, limones, champiñones).
            if (tokens.any { it in keywords || it.removeSuffix("s") in keywords || it.removeSuffix("es") in keywords }) {
                return section
            }
        }

        return OTHER
    }

    /**
     * Lowercased, accents folded ("piña" → "pina", "jamón" → "jamon"), punctuation replaced by
     * spaces, whitespace collapsed. Folding first is what keeps a Spanish name in one piece — the
     * `[^a-z0-9]` pass used to split "piña" into "pi a".
     */
    internal fun normalize(raw: String): String =
        Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()

    private val PLURAL_ENDINGS = listOf("", "s", "es")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val WHITESPACE = Regex("\\s+")

    private val FROZEN_OVERRIDE_PHRASES = listOf("frozen", "ice cream", "congelado", "congelada", "congelados", "congeladas")
    private val PANTRY_OVERRIDE_PHRASES = listOf("canned", "tinned", "jarred", "tin of", "en lata", "en conserva")

    /** Phrases are checked before tokens; PANTRY leads only here — see the class doc. */
    private val PHRASE_PRIORITY = listOf(
        PANTRY, SPICES_AND_BAKING, DAIRY_AND_EGGS, MEAT_AND_SEAFOOD, BAKERY, BEVERAGES, PRODUCE,
    )

    private val TOKEN_PRIORITY = listOf(
        SPICES_AND_BAKING, DAIRY_AND_EGGS, MEAT_AND_SEAFOOD, BAKERY, BEVERAGES, PRODUCE, PANTRY,
    )

    private val englishPhraseRules: Map<GrocerySection, List<String>> = mapOf(
        SPICES_AND_BAKING to listOf(
            "baking powder", "baking soda", "vanilla extract", "brown sugar", "icing sugar",
            "powdered sugar", "food colouring", "food coloring", "cocoa powder", "curry powder",
            "chilli powder", "chili powder", "bay leaf", "bay leaves",
        ),
        DAIRY_AND_EGGS to listOf(
            "almond milk", "oat milk", "soy milk", "soya milk", "sour cream", "cream cheese",
            "double cream", "single cream", "heavy cream", "whipping cream", "creme fraiche",
            "cottage cheese", "greek yogurt", "greek yoghurt", "condensed milk", "evaporated milk",
        ),
        MEAT_AND_SEAFOOD to listOf(
            "chicken breast", "chicken thigh", "chicken thighs", "minced beef", "ground beef",
            "minced pork", "ground pork", "pork belly", "streaky bacon", "smoked salmon",
        ),
        BAKERY to listOf(
            "bread rolls", "burger buns", "hot dog buns", "pita bread", "naan bread", "puff pastry",
            "filo pastry", "phyllo pastry", "shortcrust pastry", "sourdough loaf", "tortilla wraps",
        ),
        BEVERAGES to listOf(
            "orange juice", "apple juice", "lemon juice", "sparkling water", "coconut water",
        ),
        PRODUCE to listOf(
            "spring onion", "spring onions", "green onion", "green onions", "bell pepper",
            "bell peppers", "sweet potato", "sweet potatoes", "cherry tomato", "cherry tomatoes",
            "baby spinach", "romaine lettuce", "butternut squash", "fresh parsley", "fresh basil",
            "fresh coriander", "fresh cilantro", "fresh mint", "fresh dill", "fresh ginger",
            "salad leaves", "mixed salad",
            // Written as two words, "egg" would otherwise match dairy & eggs as a token.
            "egg plant",
        ),
        PANTRY to listOf(
            "chicken stock", "vegetable stock", "beef stock", "chicken broth", "vegetable broth",
            "olive oil", "sunflower oil", "vegetable oil", "sesame oil", "coconut oil",
            "rapeseed oil", "canola oil", "soy sauce", "soya sauce", "fish sauce", "tomato paste",
            "tomato puree", "tomato passata", "chopped tomatoes", "peanut butter", "maple syrup",
            "coconut milk", "coconut cream", "stock cube", "stock cubes", "bouillon cube",
            "chickpea flour", "dijon mustard", "worcestershire sauce", "balsamic vinegar",
            "apple cider vinegar", "egg noodle",
        ),
    )

    private val englishTokenRules: Map<GrocerySection, Set<String>> = mapOf(
        SPICES_AND_BAKING to setOf(
            "salt", "pepper", "peppercorn", "cinnamon", "nutmeg", "paprika", "cumin", "coriander",
            "turmeric", "oregano", "thyme", "rosemary", "sage", "chilli", "chili", "cayenne",
            "clove", "cardamom", "saffron", "sugar", "flour", "yeast", "cocoa", "vanilla",
            "gelatin", "gelatine", "cornstarch", "cornflour", "breadcrumb", "breadcrumbs",
            "seasoning", "spice", "spices", "extract",
        ),
        DAIRY_AND_EGGS to setOf(
            "milk", "butter", "cheese", "yogurt", "yoghurt", "cream", "egg", "eggs", "mozzarella",
            "parmesan", "cheddar", "feta", "ricotta", "mascarpone", "halloumi", "gouda", "brie",
            "ghee", "custard", "kefir",
        ),
        MEAT_AND_SEAFOOD to setOf(
            "chicken", "beef", "pork", "lamb", "veal", "turkey", "duck", "bacon", "ham", "sausage",
            "salami", "chorizo", "prosciutto", "pancetta", "mince", "steak", "brisket", "ribs",
            "fish", "salmon", "tuna", "cod", "haddock", "tilapia", "trout", "sardine", "anchovy",
            "anchovies", "prawn", "prawns", "shrimp", "crab", "lobster", "mussel", "mussels",
            "clam", "clams", "squid", "calamari", "scallop", "scallops",
        ),
        BAKERY to setOf(
            "bread", "baguette", "ciabatta", "sourdough", "brioche", "bun", "buns", "roll", "rolls",
            "bagel", "croissant", "tortilla", "pita", "naan", "focaccia", "crumpet", "muffin",
            "pastry", "loaf",
        ),
        BEVERAGES to setOf(
            "juice", "water", "coffee", "tea", "wine", "beer", "cider", "soda", "lemonade", "cola",
            "kombucha",
        ),
        PRODUCE to setOf(
            "onion", "garlic", "shallot", "leek", "tomato", "potato", "carrot", "celery",
            "cucumber", "courgette", "zucchini", "aubergine", "eggplant", "pepper", "capsicum",
            "mushroom", "broccoli", "cauliflower", "cabbage", "kale", "spinach", "lettuce",
            "rocket", "arugula", "chard", "asparagus", "pea", "peas", "bean", "beans", "corn",
            "pumpkin", "squash", "radish", "beetroot", "beet", "turnip", "parsnip", "fennel",
            "ginger", "avocado", "lemon", "lime", "orange", "apple", "banana", "pear", "grape",
            "grapes", "berry", "berries", "strawberry", "blueberry", "raspberry", "blackberry",
            "mango", "pineapple", "peach", "plum", "cherry", "melon", "watermelon", "kiwi",
            "coconut", "date", "dates", "fig", "figs", "parsley", "basil", "cilantro", "mint",
            "dill", "chive", "chives", "scallion", "sprout", "sprouts", "herb", "herbs", "salad",
        ),
        PANTRY to setOf(
            "oil", "vinegar", "rice", "pasta", "spaghetti", "penne", "macaroni", "linguine",
            "tagliatelle", "fusilli", "lasagne", "lasagna", "noodle", "noodles", "couscous",
            "quinoa", "bulgur", "barley", "lentil", "lentils", "chickpea", "chickpeas", "oat",
            "oats", "cereal", "granola", "honey", "syrup", "jam", "marmalade", "ketchup",
            "mustard", "mayonnaise", "mayo", "sauce", "stock", "broth", "bouillon", "tahini",
            "hummus", "pesto", "salsa", "tofu", "tempeh", "seitan", "nut", "nuts", "almond",
            "walnut", "cashew", "pecan", "pistachio", "peanut", "hazelnut", "seed", "seeds",
            "raisin", "raisins", "sultana", "cracker", "crackers", "crisps", "chips", "chocolate",
            "molasses", "cornmeal", "polenta", "semolina",
        ),
    )

    /**
     * Spanish staples (ADR-016), written without accents because [normalize] folds them. A few
     * dozen common ingredients rather than a dictionary; anything missing still lands in `OTHER`.
     * Plurals come free from the `s`/`es` stripping in [classify].
     */
    private val spanishPhraseRules: Map<GrocerySection, List<String>> = mapOf(
        SPICES_AND_BAKING to listOf(
            "pan rallado", "nuez moscada", "pimienta negra", "azucar glas", "levadura quimica",
        ),
        // "aceite" alone: "aceite de oliva … para freír las patatas" must not fall to the potatoes.
        // "caldo" alone: "caldo de pollo" is stock, not chicken.
        PANTRY to listOf(
            "aceite", "caldo", "tomate frito", "tomate triturado", "tomate concentrado",
            "leche de coco", "salsa de soja",
        ),
        PRODUCE to listOf("judias verdes"),
    )

    private val spanishTokenRules: Map<GrocerySection, Set<String>> = mapOf(
        SPICES_AND_BAKING to setOf(
            "sal", "pimienta", "pimenton", "comino", "canela", "tomillo", "romero", "laurel",
            "azafran", "curcuma", "clavo", "guindilla", "cayena", "especia", "azucar", "harina",
            "levadura", "maicena",
        ),
        DAIRY_AND_EGGS to setOf(
            "huevo", "leche", "mantequilla", "queso", "nata", "yogur", "requeson",
        ),
        MEAT_AND_SEAFOOD to setOf(
            "pollo", "ternera", "cerdo", "cordero", "pavo", "jamon", "beicon", "panceta",
            "salchicha", "carne", "lomo", "solomillo", "pescado", "atun", "bacalao", "merluza",
            "gamba", "langostino", "mejillon", "almeja", "calamar", "sepia", "pulpo", "anchoa",
            "boqueron", "sardina",
        ),
        BAKERY to setOf("pan", "panecillo", "bollo", "hojaldre"),
        BEVERAGES to setOf("zumo", "agua", "vino", "cerveza", "cafe", "sidra"),
        PRODUCE to setOf(
            "cebolla", "cebolleta", "ajo", "chalota", "puerro", "tomate", "patata", "papa",
            "zanahoria", "apio", "pepino", "calabacin", "berenjena", "pimiento", "champinon",
            "seta", "boletus", "brocoli", "coliflor", "col", "repollo", "espinaca", "lechuga",
            "rucula", "acelga", "esparrago", "alcachofa", "guisante", "judia", "maiz", "calabaza",
            "remolacha", "jengibre", "aguacate", "limon", "lima", "naranja", "manzana", "platano",
            "pera", "uva", "fresa", "mango", "pina", "melocoton", "cereza", "perejil", "albahaca",
            "menta", "hierbabuena", "eneldo", "cebollino", "hierba", "ensalada",
        ),
        PANTRY to setOf(
            "vinagre", "arroz", "fideo", "espagueti", "macarron", "garbanzo", "lenteja", "alubia",
            "miel", "mermelada", "mostaza", "mayonesa", "avena", "nuez", "nueces", "almendra",
            "avellana", "pinon", "cacahuete", "pasa", "aceituna", "alcaparra",
        ),
    )

    // Declared last: an object initialises its properties in order.
    private val phraseRules: Map<GrocerySection, List<String>> =
        PHRASE_PRIORITY.associateWith { englishPhraseRules[it].orEmpty() + spanishPhraseRules[it].orEmpty() }

    private val tokenRules: Map<GrocerySection, Set<String>> =
        TOKEN_PRIORITY.associateWith { englishTokenRules[it].orEmpty() + spanishTokenRules[it].orEmpty() }
}
