package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.withTransaction
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [TransactionRunner] that delegates to Room's `withTransaction`.
 */
@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: ChefAIDataBase
) : TransactionRunner {
    override suspend fun <R> withTransaction(block: suspend () -> R): R {
        return database.withTransaction { block() }
    }
}
