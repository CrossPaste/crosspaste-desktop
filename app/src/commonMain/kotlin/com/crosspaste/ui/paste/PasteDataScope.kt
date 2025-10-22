package com.crosspaste.ui.paste

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.paste.PasteData
import kotlin.reflect.KClass

interface PasteDataScope {
    val pasteData: PasteData

    val isEditing: Boolean

    // Ensure that the PasteDataScope can access the data
    // before creating it
    fun <T : Any> getPasteItem(clazz: KClass<T>): T = pasteData.getPasteItem(clazz)!!

    fun startEditing()

    fun stopEditing()
}

fun createPasteDataScope(pasteData: PasteData): PasteDataScope? =
    if (pasteData.isValid()) {
        PasteDataScopeImpl(pasteData)
    } else {
        null
    }

internal class PasteDataScopeImpl(
    override val pasteData: PasteData,
) : PasteDataScope {

    private var _isEditing by mutableStateOf(false)
    override val isEditing: Boolean get() = _isEditing

    override fun startEditing() {
        _isEditing = true
    }

    override fun stopEditing() {
        _isEditing = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteDataScopeImpl) return false

        return pasteData == other.pasteData
    }

    override fun hashCode(): Int = pasteData.hashCode()
}
