package com.tenmilelabs.chefai.core.data.local

import xyz.block.uuidv7.UUIDv7
import java.util.UUID

/**
 * Wrapper over Blocks UUIDv7 library.
 * Using a static object instead of DI for simplicity. We don't need to mock it or fake it in tests.
 */
object UuidV7Generator : IdGenerator {
    override fun newId(): UUID {
        return UUIDv7.generate()
    }
}