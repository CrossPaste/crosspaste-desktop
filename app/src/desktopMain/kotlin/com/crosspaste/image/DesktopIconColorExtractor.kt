package com.crosspaste.image

import androidx.compose.ui.graphics.Color
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.ColorUtils.hsvToRgb
import com.crosspaste.utils.ColorUtils.isNearWhiteOrBlack
import com.crosspaste.utils.ColorUtils.rgbToHsv
import com.crosspaste.utils.ColorUtils.toHexString
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.io.path.readBytes
import kotlin.math.sqrt
import kotlin.random.Random

class DesktopIconColorExtractor(
    private val userDataPathProvider: UserDataPathProvider,
) {

    private val logger = KotlinLogging.logger {}

    // Color cache
    private val colorCache = mutableMapOf<String, Color?>()

    private val mutex = Mutex()

    /**
     * Get background color from icon path (with cache)
     */
    suspend fun getBackgroundColor(source: String): Color? =
        mutex.withLock(source) {
            if (colorCache.containsKey(source)) {
                colorCache[source]
            } else {
                withContext(ioDispatcher) {
                    val color =
                        runCatching {
                            val path = userDataPathProvider.resolve("$source.png", AppFileType.ICON)
                            val bytes = path.toNioPath().readBytes()
                            extractColorFromBytes(bytes)
                        }.getOrNull()

                    logger.debug { "$source - ${color?.toHexString()}" }

                    colorCache[source] = color
                    color
                }
            }
        }

    /**
     * Extract dominant color from image byte array
     */
    private fun extractColorFromBytes(bytes: ByteArray): Color? =
        runCatching {
            val image = Image.makeFromEncoded(bytes)
            extractDominantColor(image)
        }.getOrNull()

    /**
     * Extract dominant color from Skia Image
     */
    private fun extractDominantColor(image: Image): Color {
        // Create Bitmap to read pixels
        val bitmap = Bitmap()
        bitmap.allocPixels(image.imageInfo)
        image.readPixels(bitmap)

        val width = image.width
        val height = image.height

        // Use K-means clustering algorithm to extract main colors
        val colors = extractColorsWithKMeans(bitmap, width, height)

        // Select the most appropriate color as background
        return selectBestBackgroundColor(colors)
    }

    /**
     * K-means clustering algorithm to extract main colors
     */
    private fun extractColorsWithKMeans(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        clusterCount: Int = 5,
    ): List<ColorCluster> {
        val pixels = mutableListOf<PixelColor>()
        val sampleStep = maxOf(1, minOf(width, height) / 50)

        // Sample pixels with deterministic pattern
        for (x in 0 until width step sampleStep) {
            for (y in 0 until height step sampleStep) {
                val pixel = bitmap.getColor(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 128) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                if (isNearWhiteOrBlack(r, g, b)) continue

                val hsv = rgbToHsv(r, g, b)
                val brightnessOk = (r + g + b) / 3 in 40..230
                val saturationOk = hsv[1] >= 0.2f
                if (!brightnessOk || !saturationOk) continue

                pixels.add(PixelColor(r, g, b))
            }
        }

        if (pixels.isEmpty()) return listOf(ColorCluster(128, 128, 128, 1))

        // K-means++ initialization for stability
        val clusters = kMeansPlusPlus(pixels, clusterCount)

        // K-means clustering with stable convergence
        val finalClusters = performStableKMeans(pixels, clusters)

        // Post-processing: Apply modern color preference weights
        return finalClusters
            .map { cluster ->
                val hsv = rgbToHsv(cluster.r, cluster.g, cluster.b)
                val modernWeight = getModernColorWeight(hsv[0])

                ColorCluster(
                    cluster.r,
                    cluster.g,
                    cluster.b,
                    (cluster.count * modernWeight).toInt(),
                )
            }.sortedByDescending { it.count }
    }

    /**
     * K-means++ initialization for better and more stable initial cluster centers
     * Uses deterministic seed to ensure same input produces same result
     */
    private fun kMeansPlusPlus(
        pixels: List<PixelColor>,
        k: Int,
    ): MutableList<ColorCluster> {
        val clusters = mutableListOf<ColorCluster>()

        // Use pixel data hash as seed to ensure same input produces same result
        val seed =
            pixels.fold(0) { acc, pixel ->
                acc xor (pixel.r * 31 + pixel.g * 37 + pixel.b * 41)
            }
        val random = Random(seed)

        // Select first center - choose pixel closest to median
        val firstCenter = selectMedianPixel(pixels)
        clusters.add(ColorCluster(firstCenter.r, firstCenter.g, firstCenter.b, 0))

        // Select remaining centers
        repeat(k - 1) {
            val distances =
                pixels.map { pixel ->
                    clusters.minOf { cluster -> colorDistance(pixel, cluster) }
                }

            val selectedIndex = weightedSelection(distances, random)
            val selectedPixel = pixels[selectedIndex]

            clusters.add(ColorCluster(selectedPixel.r, selectedPixel.g, selectedPixel.b, 0))
        }

        return clusters
    }

    /**
     * Select median pixel as first cluster center
     */
    private fun selectMedianPixel(pixels: List<PixelColor>): PixelColor {
        val sortedByBrightness = pixels.sortedBy { (it.r + it.g + it.b) / 3 }
        return sortedByBrightness[sortedByBrightness.size / 2]
    }

    /**
     * Deterministic weighted selection
     */
    private fun weightedSelection(
        weights: List<Double>,
        random: Random,
    ): Int {
        val totalWeight = weights.sum()
        if (totalWeight == 0.0) return 0

        val randomValue = random.nextDouble() * totalWeight
        var cumulativeWeight = 0.0

        for (i in weights.indices) {
            cumulativeWeight += weights[i]
            if (randomValue <= cumulativeWeight) {
                return i
            }
        }

        return weights.size - 1
    }

    /**
     * Improved K-means algorithm with stable convergence
     */
    private fun performStableKMeans(
        pixels: List<PixelColor>,
        initialClusters: MutableList<ColorCluster>,
    ): List<ColorCluster> {
        val clusters = initialClusters.map { it.copy() }.toMutableList()
        val maxIterations = 30
        val convergenceThreshold = 0.01
        var hasConverged = false

        for (iteration in 0 until maxIterations) {
            // Clear count for each cluster
            clusters.forEach { it.count = 0 }
            val newClusters = MutableList(clusters.size) { ColorCluster(0, 0, 0, 0) }

            // Assign pixels to nearest cluster
            pixels.forEach { pixel ->
                val nearestIndex =
                    clusters.indices.minByOrNull { i ->
                        colorDistance(pixel, clusters[i])
                    } ?: 0

                newClusters[nearestIndex].apply {
                    r += pixel.r
                    g += pixel.g
                    b += pixel.b
                    count++
                }
            }

            // Calculate movement for convergence check
            var totalMovement = 0.0

            // Update cluster centers
            for (i in clusters.indices) {
                if (newClusters[i].count > 0) {
                    val oldCluster = clusters[i]
                    val newR = newClusters[i].r / newClusters[i].count
                    val newG = newClusters[i].g / newClusters[i].count
                    val newB = newClusters[i].b / newClusters[i].count

                    // Calculate movement
                    totalMovement +=
                        sqrt(
                            (oldCluster.r - newR) * (oldCluster.r - newR) +
                                (oldCluster.g - newG) * (oldCluster.g - newG) +
                                (oldCluster.b - newB) * (oldCluster.b - newB).toDouble(),
                        )

                    clusters[i] = ColorCluster(newR, newG, newB, newClusters[i].count)
                }
            }

            // Check for convergence
            if (totalMovement < convergenceThreshold) {
                logger.debug { "K-means converged at iteration $iteration" }
                hasConverged = true
                break
            }
        }

        if (!hasConverged) {
            logger.debug { "K-means reached maximum iterations without full convergence" }
        }

        return clusters.sortedByDescending { it.count }
    }

    private fun getModernColorWeight(hue: Float): Float =
        when (hue) {
            in 220f..260f -> 1.3f // Deep blue/indigo
            in 180f..220f -> 1.25f // Cyan/teal
            in 260f..300f -> 1.25f // Lavender purple
            in 10f..30f -> 1.2f // Orange-red/coral
            in 140f..180f -> 1.2f // Emerald/mint green
            in 300f..340f -> 1.2f // Rose gold/pink
            in 200f..220f -> 1.1f // Classic blue
            in 80f..140f -> 1.1f // Classic green
            in 0f..10f, in 340f..360f -> 1.1f // Classic red
            in 40f..80f -> 0.9f // Yellow slightly downweighted
            else -> 1.0f
        }

    /**
     * Calculate hue priority (stable version)
     * Set priority based on HSV hue (H: 0-360):
     * Blue > Green > Red > Yellow > Others
     * Lower numbers have higher priority
     */
    private fun huePreferRank(h: Float): Int =
        when {
            h in 200f..260f -> 1 // Blue
            h in 80f..160f -> 2 // Green
            h in 0f..40f || h >= 320f -> 3 // Red
            h in 40f..80f -> 4 // Yellow
            else -> 5 // Others
        }

    /**
     * Calculate saturation (0-1) of a cluster
     */
    private fun saturation(cluster: ColorCluster): Float {
        val max = maxOf(cluster.r, cluster.g, cluster.b).toFloat()
        val min = minOf(cluster.r, cluster.g, cluster.b).toFloat()
        return if (max == 0f) 0f else (max - min) / max
    }

    /**
     * Select best background color (stable version)
     * 1) Sort by hue priority ascending
     * 2) Then by cluster pixel count descending
     * 3) Then by saturation descending
     * 4) Finally by unique identifier for stability
     * Finally apply brightness/saturation enhancement
     */
    private fun selectBestBackgroundColor(clusters: List<ColorCluster>): Color {
        // Filter out too dark/bright; use all if filter result is empty
        val candidates =
            clusters
                .filter { (r, g, b, _) ->
                    val brightness = (r + g + b) / 3
                    brightness in 60..200
                }.ifEmpty { clusters }

        // Stable sorting: add unique identifier as final sort criterion
        val best =
            candidates
                .sortedWith(
                    compareBy<ColorCluster> { huePreferRank(rgbToHsv(it.r, it.g, it.b)[0]) }
                        .thenByDescending { it.count }
                        .thenByDescending { saturation(it) }
                        .thenBy { it.r * 1000000 + it.g * 1000 + it.b },
                ).first()

        // Convert to Compose Color (float 0-1) and apply final enhancement
        val baseColor = Color(best.r / 255f, best.g / 255f, best.b / 255f)
        return enhanceColorForBackground(baseColor)
    }

    /**
     * Enhance color to make it more suitable as background
     * Uses precise thresholds for deterministic results
     */
    private fun enhanceColorForBackground(color: Color): Color {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()

        // Convert to HSV color space
        val hsv = rgbToHsv(r, g, b)

        // Adjust saturation and brightness with precise thresholds
        hsv[1] =
            when {
                hsv[1] < 0.6f -> 0.6f
                hsv[1] > 0.7f -> 0.7f
                else -> hsv[1]
            }

        hsv[2] =
            when {
                hsv[2] < 0.8f -> 0.8f
                hsv[2] > 0.9f -> 0.9f
                else -> hsv[2]
            }

        val rgb = hsvToRgb(hsv[0], hsv[1], hsv[2])
        return Color(rgb[0], rgb[1], rgb[2])
    }

    // Helper data classes
    private data class PixelColor(
        val r: Int,
        val g: Int,
        val b: Int,
    )

    private data class ColorCluster(
        var r: Int,
        var g: Int,
        var b: Int,
        var count: Int,
    )

    private fun colorDistance(
        p1: PixelColor,
        p2: ColorCluster,
    ): Double {
        val dr = p1.r - p2.r
        val dg = p1.g - p2.g
        val db = p1.b - p2.b
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
