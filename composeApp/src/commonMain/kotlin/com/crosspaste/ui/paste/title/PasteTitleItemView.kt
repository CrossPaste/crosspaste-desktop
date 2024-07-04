package com.crosspaste.ui.paste.title

import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.paste.preview.getPasteItem
import kotlin.io.path.name

fun getPasteTitle(pasteData: PasteData): String? {
    return if (pasteData.pasteState == PasteState.LOADING) {
        "Loading..."
    } else {
        when (pasteData.pasteType) {
            PasteType.TEXT -> getText(pasteData)
            PasteType.URL -> getUrl(pasteData)
            PasteType.HTML -> getHtml(pasteData)
            PasteType.IMAGE -> getImages(pasteData)
            PasteType.FILE -> getFiles(pasteData)
            else -> {
                "Unknown"
            }
        }
    }
}

private fun getText(pasteData: PasteData): String? {
    return pasteData.getPasteItem()?.let {
        val pasteText = it as PasteText
        pasteText.text.trim()
    }
}

private fun getUrl(pasteData: PasteData): String? {
    return pasteData.getPasteItem()?.let {
        val pasteUrl = it as PasteUrl
        pasteUrl.url.trim()
    }
}

private fun getHtml(pasteData: PasteData): String? {
    return pasteData.getPasteAppearItems().firstOrNull { it is PasteText }?.let {
        val pasteText = it as PasteText
        return pasteText.text.trim()
    } ?: run {
        pasteData.getPasteItem()?.let {
            val pasteHtml = it as PasteHtml
            pasteHtml.html
        }
    }
}

private fun getImages(pasteData: PasteData): String? {
    return pasteData.getPasteItem()?.let {
        val pasteFiles = it as PasteFiles
        pasteFiles.getFilePaths().joinToString(", ") { path -> path.fileName.name }
    }
}

private fun getFiles(pasteData: PasteData): String? {
    return pasteData.getPasteItem()?.let {
        val pasteFiles = it as PasteFiles
        pasteFiles.getFilePaths().joinToString(", ") { path -> path.fileName.name }
    }
}
