package com.tenmilelabs.chefai.core.di

import javax.inject.Qualifier

/**
 * Qualifies the unauthenticated [io.ktor.client.HttpClient] used to fetch third-party recipe
 * pages for URL import. Never carries ChefAI auth headers — see [NetworkModule.provideScraperHttpClient].
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ScraperHttpClient
