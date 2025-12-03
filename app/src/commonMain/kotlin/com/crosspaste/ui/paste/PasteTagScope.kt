package com.crosspaste.ui.paste

import androidx.compose.runtime.mutableStateOf
import com.crosspaste.paste.PasteTag
import com.crosspaste.ui.paste.PasteTagScope.Companion.isEditingMap

interface PasteTagScope {

    companion object {

        var isEditingMap = mutableStateOf<Map<Long, Boolean>>(mapOf())

        fun resetEditing() {
            isEditingMap.value = mapOf()
        }
    }

    val tag: PasteTag

    fun startEditing()

    fun stopEditing()
}

fun createPasteTagScope(tag: PasteTag): PasteTagScope = PasteTagScopeImpl(tag)

internal class PasteTagScopeImpl(
    override val tag: PasteTag,
) : PasteTagScope {

    override fun startEditing() {
        isEditingMap.value = mapOf(tag.id to true)
    }

    override fun stopEditing() {
        isEditingMap.value = isEditingMap.value.filterKeys { it != tag.id }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteTagScopeImpl) return false

        return tag == other.tag
    }

    override fun hashCode(): Int = tag.hashCode()
}
