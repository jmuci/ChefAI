package com.tenmilelabs.chefai.core.data.local

import xyz.block.uuidv7.UUIDv7
import timber.log.Timber
import java.util.UUID

/**
 * Wrapper over Blocks UUIDv7 library.
 * Using a static object instead of DI for simplicity. We don't need to mock it or fake it in tests.
 */
object UuidV7Generator {
    fun newId(): UUID {
        return try {
            UUIDv7.generate()
        } catch (e: UnsupportedClassVersionError) {
            Timber.w(e, "UUIDv7 library unavailable on this runtime, falling back to UUID.randomUUID()")
            UUID.randomUUID()
        }
    }
}
