package com.tenmilelabs.chefai.core.data.local.room

/**
 * Fake [TransactionRunner] for unit tests. Runs the block directly without wrapping
 * in a Room transaction (no Android context needed).
 */
class FakeTransactionRunner : TransactionRunner {
    override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
}
