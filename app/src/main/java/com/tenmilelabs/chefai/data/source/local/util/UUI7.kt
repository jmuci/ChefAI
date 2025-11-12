package com.tenmilelabs.chefai.data.source.local.util

import xyz.block.uuidv7.UUIDv7
import java.util.UUID

/*
    Wrapper Class for Block's UUIDv7 lib
 */
fun generateUuid7(): UUID = UUIDv7.generate() // from Blocks UUID v7 lib
