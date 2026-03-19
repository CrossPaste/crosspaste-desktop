package com.crosspaste.paste

import kotlinx.serialization.Serializable

@Serializable
data class PasteTag(
    val id: Long,
    val name: String,
    val color: Long,
    val sortOrder: Long,
) {
    companion object {

        val colors =
            listOf(
                0xFFFF3B30,
                0xFFFF9500,
                0xFFFFCC00,
                0xFF4CD964,
                0xFF007AFF,
                0xFFAF52DE,
                0xFFFF2D55,
                0xFF8E8E93,
            )

        fun createDefaultPasteTag(
            name: String,
            maxSortOrder: Long,
        ): PasteTag =
            PasteTag(
                id = -1,
                name = name,
                color = getColor(maxSortOrder + 1),
                sortOrder = maxSortOrder + 1,
            )

        fun getColor(sortOrder: Long): Long = colors[(sortOrder % colors.size).toInt()]

        fun mapper(
            id: Long,
            name: String,
            color: Long,
            sortOrder: Long,
        ): PasteTag =
            PasteTag(
                id = id,
                name = name,
                color = color,
                sortOrder = sortOrder,
            )
    }
}
