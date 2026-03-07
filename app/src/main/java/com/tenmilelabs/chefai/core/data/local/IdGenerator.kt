package com.tenmilelabs.chefai.core.data.local

import java.util.UUID

interface IdGenerator {
    fun newId(): UUID
}