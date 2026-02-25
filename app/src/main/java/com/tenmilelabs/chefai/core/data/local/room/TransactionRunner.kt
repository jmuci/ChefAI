package com.tenmilelabs.chefai.core.data.local.room

/**
 * Abstraction over Room's [androidx.room.withTransaction] for testability.
 * In production, delegates to Room's real transaction support.
 * In tests, a simple pass-through implementation can be used.
 */
interface TransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}
