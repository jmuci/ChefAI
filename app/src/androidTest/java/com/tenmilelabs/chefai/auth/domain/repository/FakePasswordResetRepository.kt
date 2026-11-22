package com.tenmilelabs.chefai.auth.domain.repository

import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import kotlinx.coroutines.CompletableDeferred

/** Scriptable [PasswordResetRepository]; every call is recorded in the matching `*Calls` list. */
class FakePasswordResetRepository(
    var requestResult: PasswordResetResult = PasswordResetResult.Success,
    var validateResult: PasswordResetResult = PasswordResetResult.Success,
    var confirmResult: PasswordResetResult = PasswordResetResult.Success,
) : PasswordResetRepository {

    val requestCalls = mutableListOf<String>()
    val validateCalls = mutableListOf<String>()
    val confirmCalls = mutableListOf<Pair<String, String>>()

    /** When set, the matching call suspends until it completes — for asserting in-flight states. */
    var requestGate: CompletableDeferred<Unit>? = null
    var validateGate: CompletableDeferred<Unit>? = null
    var confirmGate: CompletableDeferred<Unit>? = null

    override suspend fun requestReset(email: String): PasswordResetResult {
        requestCalls += email
        requestGate?.await()
        return requestResult
    }

    override suspend fun validateToken(token: String): PasswordResetResult {
        validateCalls += token
        validateGate?.await()
        return validateResult
    }

    override suspend fun confirmReset(token: String, newPassword: String): PasswordResetResult {
        confirmCalls += token to newPassword
        confirmGate?.await()
        return confirmResult
    }
}
