package com.tenmilelabs.chefai.core.data.local.room

/**
 * Abstraction over Room's `withTransaction` to allow unit testing without an Android context.
 * Production code uses [RoomTransactionRunner]; tests use a fake that runs the block directly.
 */
interface TransactionRunner {
    suspend fun <R> withTransaction(block: suspend () -> R): R
}
