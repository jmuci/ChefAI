package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableEntity
import java.util.UUID

@Entity(
    tableName = "meal_plans",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("userId"),
        Index(value = ["syncState", "updatedAt"]),
        Index("householdId")
    ]
)
data class MealPlanEntity(
    @PrimaryKey override val uuid: UUID,
    val userId: UUID,
    val name: String,
    val status: String,
    val preferencesJson: String,
    val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING,
    /**
     * Null for a personal plan (unchanged behaviour). Non-null means the plan is shared with a
     * household — see ADR-014. [userId] still names the plan's actual owner in both cases; a
     * shared plan is not re-owned by the household, only made visible/editable to its members.
     *
     * Deliberately no local foreign key to `households.uuid` — see [HouseholdEntity]'s doc for
     * why the household cache tables can't be an FK target here.
     */
    val householdId: UUID? = null,
) : SyncableEntity
