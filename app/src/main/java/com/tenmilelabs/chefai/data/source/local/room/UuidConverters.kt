package com.tenmilelabs.chefai.data.source.local.room

import androidx.room.TypeConverter
import com.tenmilelabs.chefai.data.source.local.util.toBytes
import com.tenmilelabs.chefai.data.source.local.util.toUuid
import java.util.UUID

class UuidConverters {
    @TypeConverter
    fun fromUuid(uuid: UUID): ByteArray = uuid.toBytes()

    @TypeConverter
    fun toUuid(bytes: ByteArray): UUID = bytes.toUuid()
}