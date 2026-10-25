package com.tenmilelabs.recipescraper.jsonld

/**
 * Minimal, hand-authored HTML fixtures exercising the JSON-LD extraction shapes seen in the wild.
 * Deliberately not copies of real sites — see the plan this module was built from.
 */
internal object JsonLdFixtures {

    val PLAIN_OBJECT = """
        <html><head>
        <script type="application/ld+json">
        {
          "@context": "https://schema.org/",
          "@type": "Recipe",
          "name": "Simple Pancakes",
          "description": "Fluffy <b>pancakes</b> for breakfast.",
          "image": "https://example.com/pancakes.jpg",
          "prepTime": "PT10M",
          "cookTime": "PT15M",
          "recipeYield": "4 servings",
          "recipeIngredient": ["2 cups all-purpose flour", "1 cup milk", "2 eggs"],
          "recipeInstructions": ["Mix dry ingredients.", "Whisk in milk and eggs.", "Cook on a griddle."],
          "keywords": "breakfast, easy",
          "author": {"@type": "Person", "name": "Jane Doe"}
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val GRAPH_WRAPPER = """
        <html><head>
        <script type="application/ld+json">
        {
          "@context": "https://schema.org",
          "@graph": [
            {"@type": "WebPage", "name": "Some Page"},
            {
              "@type": "Recipe",
              "name": "Graph Recipe",
              "recipeIngredient": ["1 cup sugar"],
              "recipeInstructions": ["Cream the sugar.", "Bake it."]
            }
          ]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val TYPE_ARRAY = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": ["NewsArticle", "Recipe"],
          "name": "Multi-Typed Recipe",
          "recipeIngredient": ["1 lemon"],
          "recipeInstructions": ["Squeeze the lemon."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val HOW_TO_SECTIONS = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Sectioned Recipe",
          "recipeIngredient": ["1 cup rice"],
          "recipeInstructions": [
            {
              "@type": "HowToSection",
              "name": "Prep",
              "itemListElement": [
                {"@type": "HowToStep", "text": "Rinse the rice."},
                {"@type": "HowToStep", "text": "Soak for 10 minutes."}
              ]
            },
            {
              "@type": "HowToSection",
              "name": "Cook",
              "itemListElement": [
                {"@type": "HowToStep", "text": "Boil until tender."}
              ]
            }
          ]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val STRING_INSTRUCTIONS = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Blob Instructions Recipe",
          "recipeIngredient": ["1 potato"],
          "recipeInstructions": "Peel the potato.\nBoil it.\nMash it."
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val IMAGE_OBJECT = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Image Object Recipe",
          "image": {"@type": "ImageObject", "url": "https://example.com/dish.jpg"},
          "recipeIngredient": ["1 cup broth"],
          "recipeInstructions": ["Heat the broth."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val MULTIPLE_BLOCKS_ONE_MALFORMED = """
        <html><head>
        <script type="application/ld+json">
        { this is not valid json at all
        </script>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Recovered Recipe",
          "recipeIngredient": ["1 cup water"],
          "recipeInstructions": ["Boil the water."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val NAME_ONLY = """
        <html><head>
        <script type="application/ld+json">
        {"@type": "Recipe", "name": "Just A Name"}
        </script>
        </head><body></body></html>
    """.trimIndent()

    val INSTRUCTIONS_ONLY = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Instructions Only Recipe",
          "recipeInstructions": ["Do the thing.", "Do another thing."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val OBJECT_INGREDIENTS = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "HowToSupply Ingredients Recipe",
          "recipeIngredient": [
            {"@type": "HowToSupply", "name": "2 cups flour"},
            {"@type": "HowToSupply", "text": "1 cup milk"}
          ],
          "recipeInstructions": ["Mix it all together."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val SINGLE_STRING_INGREDIENT = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Single String Ingredient Recipe",
          "recipeIngredient": "1 cup rice",
          "recipeInstructions": ["Cook the rice."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val WITH_NUTRITION = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Nutrition Recipe",
          "recipeIngredient": ["1 cup rice"],
          "recipeInstructions": ["Cook the rice."],
          "nutrition": {
            "@type": "NutritionInformation",
            "calories": "270 calories",
            "proteinContent": "12 g"
          }
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val IMPLAUSIBLE_NUTRITION = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Implausible Nutrition Recipe",
          "recipeIngredient": ["1 cup rice"],
          "recipeInstructions": ["Cook the rice."],
          "nutrition": {
            "@type": "NutritionInformation",
            "calories": "99999",
            "proteinContent": "9999 g"
          }
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val ZERO_AND_DECIMAL_NUTRITION = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Zero And Decimal Nutrition Recipe",
          "recipeIngredient": ["1 cup water"],
          "recipeInstructions": ["Drink the water."],
          "nutrition": {
            "@type": "NutritionInformation",
            "calories": "0 calories",
            "proteinContent": "12.5 g"
          }
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    val GROUPED_INGREDIENTS = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Grouped Ingredients Recipe",
          "recipeIngredient": [
            ["1 cup sugar", "2 eggs"],
            ["1 tsp vanilla"]
          ],
          "recipeInstructions": ["Combine everything."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    // --- Language detection ---

    /** Spanish text, an explicit `inLanguage` and a matching `<html lang>` — every signal agrees. */
    val SPANISH_RECIPE = """
        <html lang="es"><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Tortilla de patatas",
          "inLanguage": "es-ES",
          "description": "La tortilla de patatas clásica, jugosa y con cebolla.",
          "recipeIngredient": [
            "6 huevos",
            "500 g de patatas",
            "1 cebolla",
            "200 ml de aceite de oliva",
            "1 pizca de sal",
            "2 a 3 cucharadas de leche"
          ],
          "recipeInstructions": [
            "Pelar y cortar las patatas en láminas finas.",
            "Freír las patatas con la cebolla en el aceite a fuego medio durante 20 minutos.",
            "Batir los huevos con la sal y mezclar con las patatas.",
            "Cuajar la tortilla en la sartén por los dos lados."
          ]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    /** Spanish text on a page whose theme left `<html lang="en-US">` at its template default. */
    val MISMATCHED_LANG = """
        <html lang="en-US"><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Lentejas con chorizo",
          "recipeIngredient": [
            "300 g de lentejas",
            "1 chorizo",
            "1 cebolla",
            "2 dientes de ajo",
            "1 cucharada de pimentón",
            "Sal al gusto"
          ],
          "recipeInstructions": [
            "Picar la cebolla y el ajo y sofreír en una olla con aceite.",
            "Añadir el chorizo en rodajas y el pimentón.",
            "Agregar las lentejas y cubrir con agua.",
            "Cocinar a fuego lento durante 40 minutos hasta que estén tiernas."
          ]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    /** Spanish text with no language markup anywhere. */
    val NO_LANG_METADATA = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Arroz con leche",
          "recipeIngredient": [
            "1 litro de leche",
            "100 g de arroz",
            "150 g de azúcar",
            "1 rama de canela",
            "La piel de un limón"
          ],
          "recipeInstructions": [
            "Calentar la leche con la canela y la piel del limón.",
            "Añadir el arroz y cocinar a fuego lento durante 40 minutos.",
            "Agregar el azúcar y mezclar hasta que se disuelva."
          ]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    /** `inLanguage` as a schema.org `Language` object rather than a bare tag. */
    val LANGUAGE_OBJECT = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Language Object Recipe",
          "inLanguage": {"@type": "Language", "name": "Spanish", "alternateName": "es"},
          "recipeIngredient": ["1 taza de arroz"]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()
}
