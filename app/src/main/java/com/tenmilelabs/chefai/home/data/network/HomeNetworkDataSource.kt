package com.tenmilelabs.chefai.home.data.network

interface HomeNetworkDataSource {
    suspend fun fetchLayout(ifNoneMatch: String? = null): HomeLayoutNetworkResult
}

sealed interface HomeLayoutNetworkResult {
    data class Modified(
        val rawJson: String,
        val eTag: String? = null,
    ) : HomeLayoutNetworkResult

    data object NotModified : HomeLayoutNetworkResult
}
