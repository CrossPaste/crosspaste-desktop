package com.crosspaste.mouse

import com.crosspaste.platform.macos.api.MacosApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Reads the local monitor list from `NSScreen` via the Swift dylib so we
 * can include each display's marketing name (e.g. "DELL U2725QE",
 * "LG SDQHD"). AWT's `GraphicsDevice` doesn't expose that — the only place
 * macOS surfaces it through a stable public API is `NSScreen.localizedName`
 * (10.15+).
 *
 * If the native call fails for any reason (dylib missing, JSON corrupted,
 * security context restricted), we fall back to the AWT provider so the
 * canvas still renders rectangles — names will just be missing.
 */
class MacosLocalScreensProvider(
    private val fallback: LocalScreensProvider = AwtLocalScreensProvider(),
) : LocalScreensProvider {

    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    override fun snapshot(): List<ScreenInfo> {
        val raw =
            runCatching { MacosApi.getString(MacosApi.INSTANCE.getLocalScreensJson()) }
                .getOrElse { e ->
                    logger.warn(e) { "getLocalScreensJson native call failed; falling back to AWT" }
                    null
                }
                ?: return fallback.snapshot()
        val parsed =
            runCatching { json.decodeFromString(ListSerializer(MacosScreenJson.serializer()), raw) }
                .getOrElse { e ->
                    logger.warn(e) { "failed to parse getLocalScreensJson output; falling back to AWT" }
                    null
                } ?: return fallback.snapshot()
        if (parsed.isEmpty()) return fallback.snapshot()
        return parsed.map { it.toScreenInfo().withWallpaper() }
    }

    private fun ScreenInfo.withWallpaper(): ScreenInfo {
        val path =
            runCatching { MacosApi.getString(MacosApi.INSTANCE.getDesktopWallpaperPng(id)) }
                .getOrElse { e ->
                    logger.warn(e) { "getDesktopWallpaperPng failed for displayId=$id" }
                    null
                }
        return copy(wallpaperPath = path?.takeIf { it.isNotBlank() })
    }

    @Serializable
    private data class MacosScreenJson(
        val id: Int,
        val name: String? = null,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        @SerialName("scaleFactor") val scaleFactor: Double,
        @SerialName("isPrimary") val isPrimary: Boolean,
    ) {
        fun toScreenInfo(): ScreenInfo =
            ScreenInfo(
                id = id,
                width = width,
                height = height,
                x = x,
                y = y,
                scaleFactor = scaleFactor,
                isPrimary = isPrimary,
                name = name?.takeIf { it.isNotBlank() },
            )
    }
}
