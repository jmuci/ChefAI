package com.tenmilelabs.chefai.core.data.sync.mapper

import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.core.data.local.room.TagEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncAllergenDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncIngredientDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncLabelDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeIngredientDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeStepDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncSourceClassificationDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncTagDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncCreatorDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncMealPlanDayDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncMealPlanDto
import java.util.UUID

// --- Push direction: Room entities → DTO ---

fun RecipeEntity.toSyncDto(
    steps: List<RecipeStepEntity>,
    ingredients: List<RecipeIngredientEntity>,
    tagCrossRefs: List<RecipeTagCrossRef>,
    labelCrossRefs: List<RecipeLabelCrossRef>
): SyncRecipeDto = SyncRecipeDto(
    uuid = uuid.toString(),
    title = title,
    description = description,
    imageUrl = imageUrl,
    imageUrlThumbnail = imageUrlThumbnail,
    imageBlobId = imageBlobId,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    creatorId = creatorId.toString(),
    recipeExternalUrl = recipeExternalUrl,
    privacy = privacy.name,
    version = version,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    steps = steps.map { it.toSyncDto() },
    ingredients = ingredients.map { it.toSyncDto() },
    tagIds = tagCrossRefs.map { it.tagId.toString() },
    labelIds = labelCrossRefs.map { it.labelId.toString() }
)

fun RecipeStepEntity.toSyncDto(): SyncRecipeStepDto = SyncRecipeStepDto(
    uuid = uuid.toString(),
    orderIndex = orderIndex,
    instruction = instruction
)

fun RecipeIngredientEntity.toSyncDto(): SyncRecipeIngredientDto = SyncRecipeIngredientDto(
    ingredientId = ingredientId.toString(),
    quantity = quantity,
    unit = unit
)

// --- Pull direction: DTO → Room entities ---

fun SyncAllergenDto.toAllergenEntity(): AllergenEntity = AllergenEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

fun SyncSourceClassificationDto.toSourceClassificationEntity(): SourceClassificationEntity = SourceClassificationEntity(
    uuid = UUID.fromString(uuid),
    category = category,
    subcategory = subcategory,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

fun SyncIngredientDto.toIngredientEntity(): IngredientEntity = IngredientEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    allergenId = allergenId?.let { UUID.fromString(it) },
    sourcePrimaryId = sourcePrimaryId?.let { UUID.fromString(it) },
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

/**
 * [localImagePath] isn't part of [SyncRecipeDto] — it's device-local and never travels through
 * sync (see ADR-010 Decision 6) — so the caller must pass through whatever this device already had
 * for the row, or a pull immediately after caching an image (e.g. the post-mutation push/pull that
 * follows every import) overwrites it back to null via this entity's full-row upsert.
 *
 * [caloriesPerServing]/[proteinGramsPerServing] aren't part of [SyncRecipeDto] yet either (Phase 4
 * of the nutrition feature, not yet built) — same treatment: the caller passes through whatever
 * this device already had, so a pull doesn't silently erase calories/protein the user just entered
 * locally before the backend can round-trip them.
 *
 * `imageBlobId` needs no such threading: it is server-owned, so the DTO's value is by definition
 * the authoritative one (ADR-011 / Stage 2 D1).
 */
fun SyncRecipeDto.toRecipeEntity(
    localImagePath: String?,
    caloriesPerServing: Int? = null,
    proteinGramsPerServing: Int? = null,
): RecipeEntity = RecipeEntity(
    uuid = UUID.fromString(uuid),
    title = title,
    description = description,
    imageUrl = imageUrl,
    imageUrlThumbnail = imageUrlThumbnail,
    localImagePath = localImagePath,
    imageBlobId = imageBlobId,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    caloriesPerServing = caloriesPerServing,
    proteinGramsPerServing = proteinGramsPerServing,
    creatorId = UUID.fromString(creatorId),
    recipeExternalUrl = recipeExternalUrl,
    privacy = RecipePrivacy.valueOf(privacy.uppercase()),
    version = version,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

fun SyncRecipeDto.toStepEntities(): List<RecipeStepEntity> =
    steps.map { step ->
        RecipeStepEntity(
            uuid = UUID.fromString(step.uuid),
            recipeId = UUID.fromString(uuid),
            orderIndex = step.orderIndex,
            instruction = step.instruction,
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }

fun SyncRecipeDto.toIngredientEntities(): List<RecipeIngredientEntity> =
    ingredients.map { ing ->
        RecipeIngredientEntity(
            recipeId = UUID.fromString(uuid),
            ingredientId = UUID.fromString(ing.ingredientId),
            quantity = ing.quantity,
            unit = ing.unit,
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }

fun SyncRecipeDto.toTagCrossRefs(): List<RecipeTagCrossRef> =
    tagIds.map { tagId ->
        RecipeTagCrossRef(
            recipeId = UUID.fromString(uuid),
            tagId = UUID.fromString(tagId),
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }

fun SyncRecipeDto.toLabelCrossRefs(): List<RecipeLabelCrossRef> =
    labelIds.map { labelId ->
        RecipeLabelCrossRef(
            recipeId = UUID.fromString(uuid),
            labelId = UUID.fromString(labelId),
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }

fun SyncTagDto.toTagEntity(): TagEntity = TagEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

fun SyncLabelDto.toLabelEntity(): LabelEntity = LabelEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

fun SyncCreatorDto.toUserEntity(): UserEntity = UserEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    email = email.orEmpty(),
    avatarUrl = avatarUrl.orEmpty(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

// --- Meal Plan: Push direction (Entity → DTO) ---

fun MealPlanEntity.toSyncDto(days: List<MealPlanDayEntity>): SyncMealPlanDto = SyncMealPlanDto(
    uuid = uuid.toString(),
    name = name,
    status = status,
    preferencesJson = preferencesJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    days = days.map { it.toSyncDto() }
)

fun MealPlanDayEntity.toSyncDto(): SyncMealPlanDayDto = SyncMealPlanDayDto(
    uuid = uuid.toString(),
    dayIndex = dayIndex,
    dinnerRecipeId = dinnerRecipeId?.toString(),
    lunchRecipeId = lunchRecipeId?.toString()
)

// --- Meal Plan: Pull direction (DTO → Entity) ---

fun SyncMealPlanDto.toMealPlanEntity(userId: UUID): MealPlanEntity = MealPlanEntity(
    uuid = UUID.fromString(uuid),
    userId = userId,
    name = name,
    status = status,
    preferencesJson = preferencesJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

fun SyncMealPlanDayDto.toMealPlanDayEntity(mealPlanId: UUID): MealPlanDayEntity = MealPlanDayEntity(
    uuid = UUID.fromString(uuid),
    mealPlanId = mealPlanId,
    dayIndex = dayIndex,
    dinnerRecipeId = dinnerRecipeId?.let { UUID.fromString(it) },
    lunchRecipeId = lunchRecipeId?.let { UUID.fromString(it) }
)
