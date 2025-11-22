package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.TypeConverter
import com.tenmilelabs.chefai.core.data.local.util.toBytes
import com.tenmilelabs.chefai.core.data.local.util.toUuid
import java.util.UUID

class UuidConverters {
    @TypeConverter
    fun fromUuid(uuid: UUID): ByteArray = uuid.toBytes()

    @TypeConverter
    fun toUuid(bytes: ByteArray): UUID = bytes.toUuid()
}