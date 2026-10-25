package com.tenmilelabs.chefai.developeroptions.data.network

import com.tenmilelabs.chefai.developeroptions.domain.model.ApiBaseUrls
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import com.tenmilelabs.chefai.developeroptions.domain.repository.ApiEnvironmentRepository
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.first

/**
 * Debug-only Ktor plugin that points ChefAI requests at the environment chosen in Developer
 * options.
 *
 * Every ChefAI endpoint is built as an absolute URL from `BuildConfig.API_BASE_URL` (the local
 * backend in debug), so rather than thread a provider through every API service this rewrites
 * that prefix on the way out. Requests to any other host — the static recipe feed, for one — are
 * left alone. Installed only on debug builds; release has nothing to rewrite.
 */
val ApiEnvironmentInterceptor = createClientPlugin("ApiEnvironmentInterceptor", ::ApiEnvironmentInterceptorConfig) {
    val repository = pluginConfig.repository
    val baseUrls = pluginConfig.baseUrls

    onRequest { request, _ ->
        val environment = repository.environment.first()
        val rewritten = rewriteForEnvironment(request.url.buildString(), baseUrls, environment)
        if (rewritten != null) request.url.takeFrom(rewritten)
    }
}

class ApiEnvironmentInterceptorConfig {
    lateinit var repository: ApiEnvironmentRepository
    lateinit var baseUrls: ApiBaseUrls
}

/**
 * The URL [url] should be sent to under [environment], or `null` when it should go out unchanged —
 * either because [environment] is the one endpoints are already built against, or because [url]
 * is not a ChefAI endpoint at all.
 */
internal fun rewriteForEnvironment(
    url: String,
    baseUrls: ApiBaseUrls,
    environment: ApiEnvironment,
): String? {
    val target = baseUrls.urlFor(environment).trimEnd('/')
    val source = baseUrls.local.trimEnd('/')
    if (target == source) return null
    // A prefix match alone would also catch `http://10.0.2.2:80800/…`; require a path boundary.
    val matches = url == source || url.startsWith("$source/") || url.startsWith("$source?")
    return if (matches) target + url.removePrefix(source) else null
}
