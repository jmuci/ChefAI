package com.tenmilelabs.chefai.home.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Sealed interface representing all server-driven UI component types.
 *
 * The server sends a JSON payload with a list of these components. The client
 * resolves each type to a Composable via [ComponentRenderer]. Unknown types
 * are deserialized to [Unknown] and silently skipped in rendering.
 *
 * Uses a custom [ComponentModelSerializer] instead of @JsonClassDiscriminator
 * to gracefully handle unrecognized component types without crashing.
 */
@Serializable(with = ComponentModelSerializer::class)
sealed interface ComponentModel {
    val id: String

    @Serializable
    @SerialName("section_header")
    data class SectionHeader(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        val actionText: String? = null,
        val actionUrl: String? = null,
    ) : ComponentModel

    @Serializable
    @SerialName("carousel")
    data class Carousel(
        override val id: String,
        val items: List<ComponentModel> = emptyList(),
    ) : ComponentModel

    /** A large card backed by a recipe. Resolved via [recipeId] against Room. */
    @Serializable
    @SerialName("large_card")
    data class LargeCard(
        override val id: String,
        val recipeId: String? = null,
    ) : ComponentModel

    /** A compact square card backed by a recipe. Resolved via [recipeId] against Room. */
    @Serializable
    @SerialName("squared_card")
    data class SquaredCard(
        override val id: String,
        val recipeId: String? = null,
    ) : ComponentModel

    /** A horizontal list card backed by a recipe. Resolved via [recipeId] against Room. */
    @Serializable
    @SerialName("list_card")
    data class ListCard(
        override val id: String,
        val recipeId: String? = null,
    ) : ComponentModel

    /** Catch-all for unrecognized component types. Silently skipped in rendering. */
    data class Unknown(
        override val id: String = "unknown",
        val originalType: String = "unknown",
    ) : ComponentModel
}

/**
 * Custom polymorphic serializer for [ComponentModel] that reads and writes the "type"
 * discriminator field explicitly.
 *
 * Implements [KSerializer] directly (rather than extending [JsonContentPolymorphicSerializer])
 * so that we own both the serialization and deserialization paths. The base class's `serialize`
 * is final and does not inject the "type" discriminator — without that field the cache write
 * produces JSON that deserializes every component back as [ComponentModel.Unknown].
 */
object ComponentModelSerializer : KSerializer<ComponentModel> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ComponentModel") {
        element<String>("type")
        element<String>("id")
    }

    override fun deserialize(decoder: Decoder): ComponentModel {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val type = element["type"]?.jsonPrimitive?.content
        Timber.d("Deserializing component with type: $type")
        return when (type) {
            "section_header" -> jsonDecoder.json.decodeFromJsonElement(ComponentModel.SectionHeader.serializer(), element)
            "carousel" -> jsonDecoder.json.decodeFromJsonElement(ComponentModel.Carousel.serializer(), element)
            "large_card" -> jsonDecoder.json.decodeFromJsonElement(ComponentModel.LargeCard.serializer(), element)
            "squared_card" -> jsonDecoder.json.decodeFromJsonElement(ComponentModel.SquaredCard.serializer(), element)
            "list_card" -> jsonDecoder.json.decodeFromJsonElement(ComponentModel.ListCard.serializer(), element)
            else -> {
                val id = element["id"]?.jsonPrimitive?.content ?: "unknown_${element.hashCode()}"
                Timber.w("Got Unknown Component from the Backend with id $id and type $type")
                ComponentModel.Unknown(id = id, originalType = type ?: "missing_type")
            }
        }
    }

    override fun serialize(encoder: Encoder, value: ComponentModel) {
        val jsonEncoder = encoder as JsonEncoder
        val typeValue = when (value) {
            is ComponentModel.SectionHeader -> "section_header"
            is ComponentModel.Carousel -> "carousel"
            is ComponentModel.LargeCard -> "large_card"
            is ComponentModel.SquaredCard -> "squared_card"
            is ComponentModel.ListCard -> "list_card"
            is ComponentModel.Unknown -> value.originalType
        }
        val innerElement: JsonObject = when (value) {
            is ComponentModel.SectionHeader -> jsonEncoder.json.encodeToJsonElement(ComponentModel.SectionHeader.serializer(), value).jsonObject
            is ComponentModel.Carousel -> jsonEncoder.json.encodeToJsonElement(ComponentModel.Carousel.serializer(), value).jsonObject
            is ComponentModel.LargeCard -> jsonEncoder.json.encodeToJsonElement(ComponentModel.LargeCard.serializer(), value).jsonObject
            is ComponentModel.SquaredCard -> jsonEncoder.json.encodeToJsonElement(ComponentModel.SquaredCard.serializer(), value).jsonObject
            is ComponentModel.ListCard -> jsonEncoder.json.encodeToJsonElement(ComponentModel.ListCard.serializer(), value).jsonObject
            is ComponentModel.Unknown -> buildJsonObject { put("id", value.id) }
        }
        jsonEncoder.encodeJsonElement(buildJsonObject {
            put("type", typeValue)
            innerElement.forEach { (k, v) -> put(k, v) }
        })
    }
}

/**
 * Serializer for [ComponentModel.Unknown] — needed for cases where Unknown
 * instances are serialized (e.g., in tests). Writes a minimal JSON object.
 */
object UnknownComponentModelSerializer : KSerializer<ComponentModel.Unknown> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ComponentModel.Unknown") {
        element<String>("type")
        element<String>("id")
    }

    override fun serialize(encoder: Encoder, value: ComponentModel.Unknown) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            JsonObject(
                mapOf(
                    "type" to kotlinx.serialization.json.JsonPrimitive(value.originalType),
                    "id" to kotlinx.serialization.json.JsonPrimitive(value.id),
                )
            )
        )
    }

    override fun deserialize(decoder: Decoder): ComponentModel.Unknown {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val id = jsonObject["id"]?.jsonPrimitive?.content ?: "unknown"
        val type = jsonObject["type"]?.jsonPrimitive?.content ?: "unknown"
        return ComponentModel.Unknown(id = id, originalType = type)
    }
}
