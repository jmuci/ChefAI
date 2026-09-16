package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A read-through cache of the signed-in user's household, refreshed wholesale by
 * [com.tenmilelabs.chefai.household.data.repository.DefaultHouseholdRepository] — not a
 * [com.tenmilelabs.chefai.core.data.local.util.SyncableEntity]. Household membership is
 * authorization state the server owns outright, not offline-editable data with a dirty queue, so
 * there is nothing here to push. See ADR-014.
 *
 * No local foreign key from [com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
 * .householdId] to this table's [uuid]: this table is deleted and reinserted wholesale on every
 * refresh (the same idiom [MealPlanDayEntity] already uses for a plan's days), and a real FK would
 * either cascade-delete meal plans on every refresh or block the refresh outright.
 */
@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val uuid: UUID,
    val name: String,
    val ownerId: UUID,
    val createdAt: Long,
    val updatedAt: Long,
)
