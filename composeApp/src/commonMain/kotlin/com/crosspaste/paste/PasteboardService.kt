package com.crosspaste.paste

import com.crosspaste.config.ConfigManager
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteRealm
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.channels.Channel
import org.mongodb.kbson.ObjectId

interface PasteboardService : PasteboardMonitor {

    val logger: KLogger

    var owner: Boolean

    val pasteRealm: PasteRealm

    val configManager: ConfigManager

    val pasteboardChannel: Channel<suspend () -> Unit>

    suspend fun tryWritePasteboard(
        id: ObjectId,
        pasteItem: PasteItem,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
        updateCreateTime: Boolean = false,
    )

    suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
        primary: Boolean = false,
        updateCreateTime: Boolean = false,
    )

    suspend fun tryWriteRemotePasteboard(pasteData: PasteData)

    suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData)

    suspend fun clearRemotePasteboard(pasteData: PasteData)
}
