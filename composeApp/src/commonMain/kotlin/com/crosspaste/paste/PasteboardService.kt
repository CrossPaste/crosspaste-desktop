package com.crosspaste.paste

import com.crosspaste.app.AppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.channels.Channel
import java.awt.datatransfer.ClipboardOwner

interface PasteboardService : PasteboardMonitor, ClipboardOwner {

    val logger: KLogger

    var owner: Boolean

    val appWindowManager: AppWindowManager

    val pasteDao: PasteDao

    val configManager: ConfigManager

    val pasteboardChannel: Channel<suspend () -> Unit>

    suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
    )

    suspend fun tryWriteRemotePasteboard(pasteData: PasteData)

    suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData)

    suspend fun clearRemotePasteboard(pasteData: PasteData)
}
