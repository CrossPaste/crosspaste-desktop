package com.crosspaste.ui.paste.title

import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType

fun getPasteTitle(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
): String? {
    return if (pasteData.pasteState == PasteState.LOADING) {
        "Loading..."
    } else {
        when (pasteData.pasteType) {
            PasteType.TEXT -> getText(pasteData)
            PasteType.URL -> getUrl(pasteData)
            PasteType.HTML -> getHtml(pasteData)
            PasteType.IMAGE -> getImages(pasteData, userDataPathProvider)
            PasteType.FILE -> getFiles(pasteData, userDataPathProvider)
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

private fun getImages(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
): String? {
    return pasteData.getPasteItem()?.let {
        val pasteFiles = it as PasteFiles
        pasteFiles.getFilePaths(userDataPathProvider).joinToString(", ") { path -> path.name }
    }
}

private fun getFiles(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
): String? {
    return pasteData.getPasteItem()?.let {
        val pasteFiles = it as PasteFiles
        pasteFiles.getFilePaths(userDataPathProvider).joinToString(", ") { path -> path.name }
    }
}
