package com.tenmilelabs.chefai.core.data.local.util

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Convert UUID → 16-byte array for BLOB storage.
 */
fun UUID.toBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(16)
    buffer.putLong(this.mostSignificantBits)
    buffer.putLong(this.leastSignificantBits)
    return buffer.array()
}

/**
 * Convert 16-byte array → UUID.
 */
fun ByteArray.toUuid(): UUID {
    require(size == 16) { "UUID ByteArray must be exactly 16 bytes (got $size)." }
    val buffer = ByteBuffer.wrap(this)
    val high = buffer.long
    val low = buffer.long
    return UUID(high, low)
}

// Utility method used for testing and previews mainly.
fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}