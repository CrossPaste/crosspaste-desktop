package com.crosspaste.ui.clip.title

import com.crosspaste.clip.item.ClipFiles
import com.crosspaste.clip.item.ClipHtml
import com.crosspaste.clip.item.ClipText
import com.crosspaste.clip.item.ClipUrl
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.dao.clip.ClipState
import com.crosspaste.dao.clip.ClipType
import com.crosspaste.ui.clip.preview.getClipItem
import kotlin.io.path.name

fun getClipTitle(clipData: ClipData): String? {
    return if (clipData.clipState == ClipState.LOADING) {
        "Loading..."
    } else {
        when (clipData.clipType) {
            ClipType.TEXT -> getText(clipData)
            ClipType.URL -> getUrl(clipData)
            ClipType.HTML -> getHtml(clipData)
            ClipType.IMAGE -> getImages(clipData)
            ClipType.FILE -> getFiles(clipData)
            else -> {
                "Unknown"
            }
        }
    }
}

private fun getText(clipData: ClipData): String? {
    return clipData.getClipItem()?.let {
        val clipText = it as ClipText
        clipText.text.trim()
    }
}

private fun getUrl(clipData: ClipData): String? {
    return clipData.getClipItem()?.let {
        val clipUrl = it as ClipUrl
        clipUrl.url.trim()
    }
}

private fun getHtml(clipData: ClipData): String? {
    return clipData.getClipAppearItems().firstOrNull { it is ClipText }?.let {
        val clipText = it as ClipText
        return clipText.text.trim()
    } ?: run {
        clipData.getClipItem()?.let {
            val clipHtml = it as ClipHtml
            clipHtml.html
        }
    }
}

private fun getImages(clipData: ClipData): String? {
    return clipData.getClipItem()?.let {
        val clipFiles = it as ClipFiles
        clipFiles.getFilePaths().joinToString(", ") { path -> path.fileName.name }
    }
}

private fun getFiles(clipData: ClipData): String? {
    return clipData.getClipItem()?.let {
        val clipFiles = it as ClipFiles
        clipFiles.getFilePaths().joinToString(", ") { path -> path.fileName.name }
    }
}
