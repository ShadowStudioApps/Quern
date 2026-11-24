package com.quern.app.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
    val catalogs: List<CatalogDefinition> = emptyList(),
    val resources: List<ResourceDescriptor> = emptyList()
) {
    fun supportsResource(name: String, type: String? = null, id: String? = null): Boolean {
        val matchingResource = resources.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return false

        val allowedTypes = matchingResource.types ?: types
        if (type != null && allowedTypes.isNotEmpty() && allowedTypes.none { it.equals(type, ignoreCase = true) }) {
            return false
        }

        val allowedPrefixes = matchingResource.idPrefixes ?: idPrefixes
        if (id != null && !allowedPrefixes.isNullOrEmpty() && allowedPrefixes.none { id.startsWith(it) }) {
            return false
        }
        return true
    }
}

@Serializable
data class CatalogDefinition(
    val type: String,
    val id: String,
    val name: String? = null,
    val extra: List<CatalogExtra> = emptyList(),
    val extraRequired: List<String>? = null
) {
    val supportsSearch: Boolean
        get() = extra.any { it.name.equals("search", ignoreCase = true) }

    val requiredExtras: List<CatalogExtra>
        get() {
            val requiredNames = extraRequired.orEmpty().toSet()
            val explicit = extra.filter { it.isRequired == true || it.name in requiredNames }
            val missingLegacy = requiredNames
                .filter { reqName -> explicit.none { it.name == reqName } }
                .map { CatalogExtra(name = it, isRequired = true, options = null) }
            return explicit + missingLegacy
        }

    val requiresExtra: Boolean
        get() = requiredExtras.isNotEmpty()

    /**
     * Returns an empty map when no extra is required, a map of `extraName -> firstOption` when every
     * required extra (excluding free-text `search`) declares non-empty `options`, or `null` when the
     * catalog must stay off Home (e.g. search-only or required extra without options).
     */
    fun defaultHomeExtraArgs(): Map<String, String>? {
        val req = requiredExtras
        if (req.isEmpty()) return emptyMap()
        if (req.any { it.name.equals("search", ignoreCase = true) }) return null
        val resolved = LinkedHashMap<String, String>(req.size)
        for (extraItem in req) {
            val firstOpt = extraItem.options?.firstOrNull { it.isNotBlank() }?.trim()
                ?: return null
            resolved[extraItem.name] = firstOpt
        }
        return resolved
    }

    val isHomeEligible: Boolean
        get() = defaultHomeExtraArgs() != null

    fun visibilityKey(addonId: String): String = "${addonId}_${type}_${id}"
}

@Serializable
data class CatalogExtra(
    val name: String,
    val isRequired: Boolean? = false,
    val options: List<String>? = null
)

@Serializable(with = ResourceDescriptorSerializer::class)
data class ResourceDescriptor(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null
)

object ResourceDescriptorSerializer : KSerializer<ResourceDescriptor> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ResourceDescriptor")

    override fun deserialize(decoder: Decoder): ResourceDescriptor {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ResourceDescriptorSerializer only supports JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ResourceDescriptor(name = element.content)
            is JsonObject -> {
                val name = element["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val types = element["types"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                val prefixes = element["idPrefixes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ResourceDescriptor(name = name, types = types, idPrefixes = prefixes)
            }
            else -> ResourceDescriptor(name = "")
        }
    }

    override fun serialize(encoder: Encoder, value: ResourceDescriptor) {
        encoder.encodeString(value.name)
    }
}

data class InstalledAddon(
    val manifestUrl: String,
    val baseUrl: String,
    val manifest: AddonManifest,
    val enabled: Boolean = true,
    val priority: Int = 0
)

// Stremio Protocol Wire DTOs

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview> = emptyList()
)

@Serializable
data class StremioMetaPreview(
    val id: String,
    val type: String = "movie",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val released: String? = null,
    val runtime: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList()
)

@Serializable
data class StremioMetaResponse(
    val meta: StremioMetaDetail? = null
)

@Serializable
data class StremioMetaDetail(
    val id: String,
    val type: String = "movie",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val released: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val videos: List<StremioVideo> = emptyList()
)

@Serializable
data class StremioVideo(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val number: Int? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null
)

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStreamDto> = emptyList()
)

@Serializable
data class StremioStreamDto(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val externalUrl: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val subtitles: List<StremioSubtitleDto> = emptyList(),
    val behaviorHints: StremioBehaviorHints? = null
)

@Serializable
data class StremioBehaviorHints(
    val notWebReady: Boolean? = null,
    val bingeGroup: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
    val proxyHeaders: StremioProxyHeaders? = null
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String> = emptyMap(),
    val response: Map<String, String> = emptyMap()
)

@Serializable
data class StremioSubtitlesResponse(
    val subtitles: List<StremioSubtitleDto> = emptyList()
)

@Serializable
data class StremioSubtitleDto(
    val id: String? = null,
    val url: String,
    val lang: String = "und",
    @SerialName("SubEncoding")
    val encoding: String? = null
)
