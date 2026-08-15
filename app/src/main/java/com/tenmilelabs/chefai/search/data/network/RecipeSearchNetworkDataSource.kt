package com.tenmilelabs.chefai.search.data.network

interface RecipeSearchNetworkDataSource {
    suspend fun search(query: String, limit: Int, offset: Int): RecipeSearchNetworkResult
}
