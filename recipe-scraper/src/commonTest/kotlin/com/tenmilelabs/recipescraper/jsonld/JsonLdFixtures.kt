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
}
