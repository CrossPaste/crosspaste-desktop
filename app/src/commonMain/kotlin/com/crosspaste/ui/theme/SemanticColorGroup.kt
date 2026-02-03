package com.crosspaste.ui.theme

import androidx.compose.ui.graphics.Color

data class SemanticColorGroup(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
    val surface: Color,
) {
    companion object {
        // Light Theme Success - Green/Emerald palette (Tailwind green)
        val LIGHT_SUCCESS =
            SemanticColorGroup(
                color = Color(0xFF22C55E), // green-500
                onColor = Color(0xFFFFFFFF), // white
                container = Color(0xFFDCFCE7), // green-100
                onContainer = Color(0xFF14532D), // green-900
                surface = Color(0xFFF0FDF4), // green-50
            )

        // Dark Theme Success
        val DARK_SUCCESS =
            SemanticColorGroup(
                color = Color(0xFF4ADE80), // green-400
                onColor = Color(0xFF14532D), // green-900
                container = Color(0xFF14532D), // green-900
                onContainer = Color(0xFFDCFCE7), // green-100
                surface = Color(0xFF052E16), // green-950
            )

        // Light Theme Info - Blue palette (Tailwind blue)
        val LIGHT_INFO =
            SemanticColorGroup(
                color = Color(0xFF3B82F6), // blue-500
                onColor = Color(0xFFFFFFFF), // white
                container = Color(0xFFDBEAFE), // blue-100
                onContainer = Color(0xFF1E3A8A), // blue-900
                surface = Color(0xFFEFF6FF), // blue-50
            )

        // Dark Theme Info
        val DARK_INFO =
            SemanticColorGroup(
                color = Color(0xFF60A5FA), // blue-400
                onColor = Color(0xFF1E3A5F), // blue-900 variant
                container = Color(0xFF1E3A8A), // blue-900
                onContainer = Color(0xFFDBEAFE), // blue-100
                surface = Color(0xFF172554), // blue-950
            )

        // Light Theme Neutral - Gray palette (Tailwind gray)
        val LIGHT_NEUTRAL =
            SemanticColorGroup(
                color = Color(0xFF6B7280), // gray-500
                onColor = Color(0xFFFFFFFF), // white
                container = Color(0xFFF3F4F6), // gray-100
                onContainer = Color(0xFF1F2937), // gray-800
                surface = Color(0xFFF9FAFB), // gray-50
            )

        // Dark Theme Neutral
        val DARK_NEUTRAL =
            SemanticColorGroup(
                color = Color(0xFF9CA3AF), // gray-400
                onColor = Color(0xFF1F2937), // gray-800
                container = Color(0xFF374151), // gray-700
                onContainer = Color(0xFFF3F4F6), // gray-100
                surface = Color(0xFF111827), // gray-900
            )

        // Light Theme Warning - Amber palette (Tailwind amber)
        val LIGHT_WARNING =
            SemanticColorGroup(
                color = Color(0xFFD97706), // amber-600
                onColor = Color(0xFFFFFFFF), // white
                container = Color(0xFFFEF3C7), // amber-100
                onContainer = Color(0xFF78350F), // amber-900
                surface = Color(0xFFFFFBEB), // amber-50
            )

        // Dark Theme Warning
        val DARK_WARNING =
            SemanticColorGroup(
                color = Color(0xFFFBBF24), // amber-400
                onColor = Color(0xFF78350F), // amber-900
                container = Color(0xFF78350F), // amber-900
                onContainer = Color(0xFFFEF3C7), // amber-100
                surface = Color(0xFF451A03), // amber-950
            )
    }
}
