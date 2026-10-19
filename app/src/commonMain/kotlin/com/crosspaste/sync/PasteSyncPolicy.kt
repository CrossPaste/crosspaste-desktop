package com.crosspaste.sync

import com.crosspaste.config.AppConfig
import com.crosspaste.paste.PasteType

/**
 * The sender-side per-type sync switches. Applies to every path that hands this
 * device's pastes to a peer, whether pushed or pulled.
 */
fun AppConfig.isSyncEnabledForType(pasteType: PasteType): Boolean =
    when (pasteType) {
        PasteType.TEXT_TYPE -> enableSyncText
        PasteType.URL_TYPE -> enableSyncUrl
        PasteType.HTML_TYPE -> enableSyncHtml
        PasteType.RTF_TYPE -> enableSyncRtf
        PasteType.IMAGE_TYPE -> enableSyncImage
        PasteType.FILE_TYPE -> enableSyncFile
        PasteType.COLOR_TYPE -> enableSyncColor
        else -> true
    }
