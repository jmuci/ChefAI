package com.tenmilelabs.chefai.home.data.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * Custom polymorphic serializer that reads the "type" discriminator from JSON
 * and routes to the appropriate [ComponentModel] variant. Unrecognized types
 * are deserialized to [ComponentModel.Unknown] instead of throwing.
 */
object ComponentModelSerializer : JsonContentPolymorphicSerializer<ComponentModel>(ComponentModel::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ComponentModel> {
        val jsonObject = element.jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content
        Timber.d("Deserializing component with type: $type")
        return when (type) {
            "section_header" -> ComponentModel.SectionHeader.serializer()
            "carousel" -> ComponentModel.Carousel.serializer()
            "large_card" -> ComponentModel.LargeCard.serializer()
            "squared_card" -> ComponentModel.SquaredCard.serializer()
            "list_card" -> ComponentModel.ListCard.serializer()
            else -> UnknownComponentFallbackSerializer(type ?: "missing_type")
        }
    }
}

/**
 * Serializer that absorbs any JSON object and produces [ComponentModel.Unknown].
 * Extracts the "id" field if present for stable key support.
 *
 * Must implement [KSerializer] (not just [DeserializationStrategy]) because
 * [JsonContentPolymorphicSerializer] casts the returned strategy to [KSerializer]
 * internally before calling [kotlinx.serialization.json.Json.decodeFromJsonElement].
 */
private class UnknownComponentFallbackSerializer(
    private val originalType: String,
) : KSerializer<ComponentModel.Unknown> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ComponentModel.Unknown") {
        element<String>("id", isOptional = true)
        element<String>("type", isOptional = true)
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
        val id = jsonObject["id"]?.jsonPrimitive?.content ?: "unknown_${jsonObject.hashCode()}"
        Timber.w("Got Unknown Component from the Backend with id $id")
        return ComponentModel.Unknown(id = id, originalType = originalType)
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
