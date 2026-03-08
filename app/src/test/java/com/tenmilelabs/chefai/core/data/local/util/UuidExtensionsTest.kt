package com.tenmilelabs.chefai.core.data.local.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class UuidExtensionsTest {

    @Test
    fun `uuid converts to bytes and back correctly`() {
        val original = UuidV7Generator.newId()

        val bytes = original.toBytes()
        val restored = bytes.toUuid()

        assertEquals("UUID should survive round trip to bytes and back", original, restored)
    }

    @Test
    fun `two different uuids produce different byte arrays`() {
        val uuid1 = UuidV7Generator.newId()
        val uuid2 = UuidV7Generator.newId()

        val bytes1 = uuid1.toBytes()
        val bytes2 = uuid2.toBytes()

        // They *could* collide in theory, but practically impossible.
        assertNotEquals(bytes1.toList(), bytes2.toList())
    }

    @Test
    fun `byte array must be 16 bytes`() {
        val badArray = ByteArray(10) { 0 }

        try {
            badArray.toUuid()
            assert(false) { "Expected IllegalArgumentException for invalid byte array size" }
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
}
