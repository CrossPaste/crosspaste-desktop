package com.crosspaste.app

import com.crosspaste.net.ResourcesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WebLocale(
    val code: String,
    val label: String,
    val path: String,
)

@Serializable
data class WebMeta(
    val locales: List<WebLocale>,
)

class CrossPasteWebService(
    private val appUrls: AppUrls,
    private val resourcesClient: ResourcesClient,
) {

    private val logger = KotlinLogging.logger {}

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private var localePathMap: Map<String, String> = ConcurrentMap()

    suspend fun refresh() {
        runCatching {
            val metaUrl = "${appUrls.homeUrl}/api/meta.json"
            resourcesClient
                .request(metaUrl)
                .getOrNull()
                ?.let { response ->
                    val text = response.getBodyAsText()
                    val meta = json.decodeFromString<WebMeta>(text)
                    localePathMap = meta.locales.associate { it.code to it.path }
                    logger.info { "Web locale config loaded: ${localePathMap.keys}" }
                }
        }.onFailure { e ->
            logger.warn { "Failed to fetch web locale config: ${e.message}" }
        }
    }

    fun getWebUrl(
        language: String,
        path: String = "",
    ): String {
        val localePath = resolveLocalePath(language)
        return "${appUrls.homeUrl}$localePath$path"
    }

    private fun resolveLocalePath(language: String): String {
        val map = localePathMap
        if (map.isNotEmpty()) {
            return map[language] ?: map["en"] ?: "/en/"
        }
        // Fallback before meta.json loads
        return if (language == "zh") "/" else "/en/"
    }
}
