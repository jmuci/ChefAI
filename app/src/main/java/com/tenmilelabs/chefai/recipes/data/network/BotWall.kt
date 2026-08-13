package com.tenmilelabs.chefai.recipes.data.network

import io.ktor.http.HttpStatusCode

/**
 * Statuses a bot manager returns to a client it doesn't like. Akamai and Cloudflare both answer
 * 403; 401, 429 and 503 cover the rate-limit and "under attack mode" variants. A 404 is
 * deliberately absent — that page really is missing, and a browser won't find it either.
 *
 * Shared by [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter] (HTML fetch) and
 * [com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage] (image fetch) — both escalate to
 * a WebView on exactly these statuses.
 */
internal val BOT_WALL_STATUSES = setOf(
    HttpStatusCode.Unauthorized,
    HttpStatusCode.Forbidden,
    HttpStatusCode.TooManyRequests,
    HttpStatusCode.ServiceUnavailable,
)
