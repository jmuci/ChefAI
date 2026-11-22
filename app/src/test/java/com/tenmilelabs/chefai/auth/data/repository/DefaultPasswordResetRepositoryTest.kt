package com.tenmilelabs.chefai.auth.data.repository

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.network.PasswordResetNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.PasswordResetNetworkResult
import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class DefaultPasswordResetRepositoryTest {

    private class FakeDataSource(
        var result: PasswordResetNetworkResult = PasswordResetNetworkResult.Success,
        var failure: Throwable? = null,
    ) : PasswordResetNetworkDataSource {
        override suspend fun requestReset(email: String) = respond()
        override suspend fun validateToken(token: String) = respond()
        override suspend fun confirmReset(token: String, newPassword: String) = respond()
        private fun respond(): PasswordResetNetworkResult = failure?.let { throw it } ?: result
    }

    private fun repository(source: FakeDataSource) = DefaultPasswordResetRepository(source)

    @Test
    fun `each network result maps to its domain result`() = runTest {
        val cases = listOf(
            PasswordResetNetworkResult.Success to PasswordResetResult.Success,
            PasswordResetNetworkResult.InvalidToken to PasswordResetResult.InvalidToken,
            PasswordResetNetworkResult.Validation("too short") to PasswordResetResult.Validation("too short"),
            PasswordResetNetworkResult.RateLimited to PasswordResetResult.RateLimited,
            PasswordResetNetworkResult.Failed(503) to PasswordResetResult.Network,
            PasswordResetNetworkResult.Failed(418) to PasswordResetResult.Unknown,
        )
        for ((network, expected) in cases) {
            val subject = repository(FakeDataSource(result = network))

            assertThat(subject.requestReset("a@b.com")).isEqualTo(expected)
            assertThat(subject.validateToken("t")).isEqualTo(expected)
            assertThat(subject.confirmReset("t", "p")).isEqualTo(expected)
        }
    }

    @Test
    fun `an IOException becomes Network`() = runTest {
        val subject = repository(FakeDataSource(failure = IOException("offline")))

        assertThat(subject.requestReset("a@b.com")).isEqualTo(PasswordResetResult.Network)
    }

    @Test
    fun `an unreadable response becomes Unknown`() = runTest {
        val subject = repository(FakeDataSource(failure = SerializationException("bad body")))

        assertThat(subject.validateToken("t")).isEqualTo(PasswordResetResult.Unknown)
    }

    @Test
    fun `cancellation is rethrown, not swallowed`() = runTest {
        val subject = repository(FakeDataSource(failure = CancellationException("cancelled")))

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { subject.confirmReset("t", "p") }
        }
    }
}
