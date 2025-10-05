package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object FileColors {
    // Primary color palette - 12 distinct colors for common file types
    val RED = Color(0xFFF56262) // MOV, AVI
    val PURPLE = Color(0xFF9C7EEA) // SWF, 3GP
    val BLUE = Color(0xFF64B5F6) // MP4, VOB
    val GRAY = Color(0xFFB4B4B4) // MKV, AAF
    val GREEN = Color(0xFF7BC67B) // FLV, MOD
    val YELLOW = Color(0xFFFFD54F) // WMV, MPEG
    val ORANGE = Color(0xFFFFB74D) // RM, M4V
    val CYAN = Color(0xFF4DD0E1) // WebM, MTS
    val PINK = Color(0xFFF48FB1) // MPG, RMVB
    val TEAL = Color(0xFF4DB6AC) // OGG, TS
    val AMBER = Color(0xFFFFCA28) // M2TS, M2V
    val INDIGO = Color(0xFF7986CB) // ASF, DVR

    // All colors in a list for algorithm use
    private val ALL_COLORS =
        listOf(
            RED,
            PURPLE,
            BLUE,
            GRAY,
            GREEN,
            YELLOW,
            ORANGE,
            CYAN,
            PINK,
            TEAL,
            AMBER,
            INDIGO,
        )

    /**
     * Gets a color based on file extension using a deterministic algorithm
     *
     * @param ext The file extension (without the dot)
     * @return A color from the palette
     */
    fun getColorForExtension(ext: String): Color {
        val normalizedExt = ext.trim().uppercase()

        // Special case handling for common video formats to match the reference image
        return when (normalizedExt) {
            "MOV" -> RED
            "SWF" -> PURPLE
            "MP4" -> BLUE
            "MKV" -> GRAY
            "FLV" -> GREEN
            "WMV" -> YELLOW
            "AVI" -> RED
            "3GP" -> PURPLE
            "VOB" -> BLUE
            "AAF" -> GRAY
            "MOD" -> GREEN
            "MPEG" -> YELLOW
            else -> {
                // For other extensions, use a hash-based approach
                val hash = ext.hashCode()
                val index = abs(hash) % ALL_COLORS.size
                ALL_COLORS[index]
            }
        }
    }
}
