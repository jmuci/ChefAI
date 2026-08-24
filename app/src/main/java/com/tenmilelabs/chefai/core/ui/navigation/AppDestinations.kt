package com.tenmilelabs.chefai.core.ui.navigation

import androidx.annotation.StringRes
import androidx.navigation.NavHostController
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs.DRAFT_ID_ARG
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs.IMPORT_URL_ARG
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs.MEAL_PLAN_ID_ARG
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs.PREFILL_URL_ARG
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs.RECIPE_ID_ARG
import com.tenmilelabs.chefai.core.ui.navigation.ScreenBaseRoutes.RECIPE_DETAILS
import java.net.URLEncoder
import java.util.UUID

/**
 * Screens used in [AppDestinations].
 */
internal object ScreenBaseRoutes {
    const val HOME = "home_screen"
    const val SEARCH = "search_screen"
    const val MEAL_PLANS = "meal_plans_screen"
    const val RECIPES = "recipes_screen"
    const val RECIPE_DETAILS = "recipe_details_screen"
    const val CREATE_RECIPE = "create_recipe_screen"
    const val RECIPE_EDITOR = "recipe_editor_screen"
    const val IMPORT_RECIPE = "import_recipe_screen"
    const val IMPORT_RECIPE_BROWSER = "import_recipe_browser_screen"
    const val SETTINGS = "settings_screen"
    const val LOGIN = "login_screen"
    const val REGISTER = "register_screen"
    const val MEAL_PLAN_DETAIL = "meal_plan_detail"
    const val MEAL_PLAN_RECIPE_DETAIL = "meal_plan_recipe_detail"
    const val SEARCH_RECIPE_DETAIL = "search_recipe_detail"
    const val MEAL_PLAN_WIZARD = "meal_plan_wizard"
    const val MEAL_PLAN_WIZARD_BASICS = "meal_plan_wizard_basics"
    const val MEAL_PLAN_WIZARD_PREFERENCES = "meal_plan_wizard_preferences"
    const val MEAL_PLAN_WIZARD_ADVANCED = "meal_plan_wizard_advanced"
}

/**
 * Arguments used in [AppDestinations]
 */
object AppDestinationArgs {
    const val RECIPE_ID_ARG = "recipeUuid"
    const val MEAL_PLAN_ID_ARG = "mealPlanId"

    /**
     * Id of a pre-seeded [com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity] the editor
     * should open in **create** mode, used by recipe URL import. Distinct from [RECIPE_ID_ARG],
     * which puts the editor in edit mode against an already-saved recipe.
     */
    const val DRAFT_ID_ARG = "draftUuid"

    /**
     * A URL to pre-fill the import screen's field with, used when the app is opened via the
     * Android share sheet (see [MainActivity][com.tenmilelabs.chefai.MainActivity]). URL-encoded
     * in the route, since the value itself is a URL containing reserved characters.
     */
    const val PREFILL_URL_ARG = "prefillUrl"

    /**
     * The URL the browser-import screen should load so the user can clear a site's bot check.
     * URL-encoded in the route for the same reason as [PREFILL_URL_ARG].
     */
    const val IMPORT_URL_ARG = "importUrl"
}

/**
 * Destinations used in the [com.tenmilelabs.chefai.MainActivity].
 */
enum class AppDestinations(
    @param:StringRes val title: Int,
    val route: String,
) {
    HOME(R.string.app_dest_title_home, ScreenBaseRoutes.HOME),
    SEARCH(R.string.app_dest_title_search, ScreenBaseRoutes.SEARCH),
    MEAL_PLANS(R.string.app_dest_title_meal_plans, ScreenBaseRoutes.MEAL_PLANS),
    RECIPES(R.string.app_dest_title_recipes, ScreenBaseRoutes.RECIPES),
    RECIPE_DETAILS(
        R.string.app_dest_title_recipe_details,
        "${ScreenBaseRoutes.RECIPE_DETAILS}/{$RECIPE_ID_ARG}"
    ),
    CREATE_RECIPE(R.string.app_dest_title_create_recipe, ScreenBaseRoutes.CREATE_RECIPE),
    RECIPE_EDITOR(
        R.string.app_dest_title_recipe_editor,
        "${ScreenBaseRoutes.RECIPE_EDITOR}" +
            "?${RECIPE_ID_ARG}={${RECIPE_ID_ARG}}" +
            "&${DRAFT_ID_ARG}={${DRAFT_ID_ARG}}"
    ),
    IMPORT_RECIPE(
        R.string.app_dest_title_import_recipe,
        "${ScreenBaseRoutes.IMPORT_RECIPE}?${PREFILL_URL_ARG}={${PREFILL_URL_ARG}}"
    ),
    IMPORT_RECIPE_BROWSER(
        R.string.app_dest_title_import_recipe_browser,
        "${ScreenBaseRoutes.IMPORT_RECIPE_BROWSER}/{$IMPORT_URL_ARG}"
    ),
    MEAL_PLAN_DETAIL(
        R.string.app_dest_title_meal_plan_detail,
        "${ScreenBaseRoutes.MEAL_PLAN_DETAIL}/{$MEAL_PLAN_ID_ARG}"
    ),
    MEAL_PLAN_RECIPE_DETAIL(
        R.string.app_dest_title_recipe_details,
        "${ScreenBaseRoutes.MEAL_PLAN_RECIPE_DETAIL}/{$RECIPE_ID_ARG}"
    ),
    SEARCH_RECIPE_DETAIL(
        R.string.app_dest_title_recipe_details,
        "${ScreenBaseRoutes.SEARCH_RECIPE_DETAIL}/{$RECIPE_ID_ARG}"
    ),
    MEAL_PLAN_WIZARD(R.string.app_dest_title_meal_plan_wizard, ScreenBaseRoutes.MEAL_PLAN_WIZARD),
    SETTINGS(R.string.app_dest_title_settings, ScreenBaseRoutes.SETTINGS),
    LOGIN(R.string.app_dest_title_login, ScreenBaseRoutes.LOGIN),
    REGISTER(R.string.app_dest_title_register, ScreenBaseRoutes.REGISTER),
}


/**
 * Models the navigation actions in the app.
 */
class NavigationActions(private val navController: NavHostController) {
    fun navigateToRecipeDetail(recipeId: UUID) {
        navController.navigate("$RECIPE_DETAILS/$recipeId")
    }

    fun navigateToCreateRecipe() {
        navController.navigate(ScreenBaseRoutes.RECIPE_EDITOR)
    }

    fun navigateToEditRecipe(recipeId: UUID) {
        navController.navigate("${ScreenBaseRoutes.RECIPE_EDITOR}?${RECIPE_ID_ARG}=$recipeId")
    }

    /**
     * @param prefillUrl a URL to pre-fill the field with, e.g. from a share-sheet intent. `null`
     *   for the normal FAB entry point, which starts with an empty field.
     */
    fun navigateToImportRecipe(prefillUrl: String? = null) {
        if (prefillUrl == null) {
            navController.navigate(ScreenBaseRoutes.IMPORT_RECIPE)
        } else {
            val encoded = URLEncoder.encode(prefillUrl, "UTF-8")
            navController.navigate("${ScreenBaseRoutes.IMPORT_RECIPE}?${PREFILL_URL_ARG}=$encoded")
        }
    }

    /**
     * Opens the in-app browser on [url] so the user can clear a site's bot check, after both the
     * HTTP fetch and the off-screen browser were refused.
     */
    fun navigateToBrowserImport(url: String) {
        val encoded = URLEncoder.encode(url, "UTF-8")
        navController.navigate("${ScreenBaseRoutes.IMPORT_RECIPE_BROWSER}/$encoded")
    }

    /**
     * Opens the editor in **create** mode against an already-persisted draft — the hand-off after a
     * recipe has been scraped from a URL. The import screen is popped so that backing out of the
     * editor returns to the recipe list rather than to a stale URL field. That also takes the
     * browser-import screen with it when the draft came via the fallback, since it sits on top of
     * the import screen.
     */
    fun navigateToEditorWithDraft(draftId: UUID) {
        navController.navigate("${ScreenBaseRoutes.RECIPE_EDITOR}?${DRAFT_ID_ARG}=$draftId") {
            popUpTo(ScreenBaseRoutes.IMPORT_RECIPE) { inclusive = true }
        }
    }

    fun navigateToLogin() {
        navController.navigate(ScreenBaseRoutes.LOGIN) {
            launchSingleTop = true
        }
    }

    fun navigateToRegister() {
        navController.navigate(ScreenBaseRoutes.REGISTER)
    }

    fun navigateToMealPlanDetail(mealPlanId: UUID) {
        navController.navigate("${ScreenBaseRoutes.MEAL_PLAN_DETAIL}/$mealPlanId")
    }

    fun navigateToMealPlanRecipeDetail(recipeId: UUID) {
        navController.navigate("${ScreenBaseRoutes.MEAL_PLAN_RECIPE_DETAIL}/$recipeId")
    }

    /**
     * Opens recipe details from the Search tab, on its own route so the bottom nav keeps Search
     * highlighted instead of falling back to Recipes (see [isRouteInSection]).
     */
    fun navigateToSearchRecipeDetail(recipeId: UUID) {
        navController.navigate("${ScreenBaseRoutes.SEARCH_RECIPE_DETAIL}/$recipeId")
    }

    fun navigateToMealPlanWizard() {
        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD)
    }

    fun navigateToHome() {
        navController.navigate(ScreenBaseRoutes.HOME) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}
