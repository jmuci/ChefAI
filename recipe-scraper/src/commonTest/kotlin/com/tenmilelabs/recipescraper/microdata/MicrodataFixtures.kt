package com.tenmilelabs.recipescraper.microdata

/** Minimal, hand-authored HTML fixtures exercising the microdata shapes seen in the wild. */
internal object MicrodataFixtures {

    val FULL_RECIPE = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
          <h1 itemprop="name">Chocolate Chip Cookies</h1>
          <p itemprop="description">Classic <b>chewy</b> cookies.</p>
          <img itemprop="image" src="https://example.com/cookies.jpg" />
          <time itemprop="prepTime" datetime="PT20M">20 mins</time>
          <time itemprop="cookTime" datetime="PT10M">10 mins</time>
          <span itemprop="recipeYield">24 cookies</span>
          <ul>
            <li itemprop="recipeIngredient">2 cups flour</li>
            <li itemprop="recipeIngredient">1 cup sugar</li>
          </ul>
          <ol>
            <li itemprop="recipeInstructions">Preheat oven to 350F.</li>
            <li itemprop="recipeInstructions">Mix ingredients.</li>
            <li itemprop="recipeInstructions">Bake for 10 minutes.</li>
          </ol>
          <span itemprop="keywords">cookies, dessert</span>
          <span itemprop="author">Baker Bob</span>
        </div>
        </body></html>
    """.trimIndent()

    val NAME_ONLY = """
        <html><body>
        <div itemscope itemtype="http://schema.org/Recipe">
          <span itemprop="name">Just A Name</span>
        </div>
        </body></html>
    """.trimIndent()

    val META_AND_LINK_VALUES = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
          <meta itemprop="name" content="Meta-Driven Recipe" />
          <meta itemprop="recipeYield" content="6" />
          <link itemprop="image" href="https://example.com/from-link.jpg" />
          <li itemprop="recipeIngredient">1 cup broth</li>
          <li itemprop="recipeInstructions">Heat the broth.</li>
        </div>
        </body></html>
    """.trimIndent()

    val NO_RECIPE_SCOPE = "<html><body><div itemscope itemtype=\"https://schema.org/Article\">hello</div></body></html>"

    val WITH_NUTRITION = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
          <span itemprop="name">Nutrition Recipe</span>
          <li itemprop="recipeIngredient">1 cup rice</li>
          <li itemprop="recipeInstructions">Cook the rice.</li>
          <div itemprop="nutrition" itemscope itemtype="https://schema.org/NutritionInformation">
            <span itemprop="calories">270 calories</span>
            <span itemprop="proteinContent">12 g</span>
          </div>
        </div>
        </body></html>
    """.trimIndent()
}
