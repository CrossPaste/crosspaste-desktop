package com.crosspaste.ui.paste

import com.crosspaste.paste.PasteData
import kotlin.reflect.KClass

interface PasteDataScope {
    val pasteData: PasteData

    // Ensure that the PasteDataScope can access the data
    // before creating it
    fun <T : Any> getPasteItem(clazz: KClass<T>): T = pasteData.getPasteItem(clazz)!!
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasteDataScopeImpl

        return pasteData == other.pasteData
    }

    override fun hashCode(): Int = pasteData.hashCode()
}
