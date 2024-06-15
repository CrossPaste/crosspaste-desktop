package com.clipevery.clip

import com.clipevery.app.AppWindowManager
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.channels.Channel
import java.awt.datatransfer.ClipboardOwner

interface ClipboardService : ClipboardMonitor, ClipboardOwner {

    val logger: KLogger

    var owner: Boolean

    val appWindowManager: AppWindowManager

    val clipDao: ClipDao

    val configManager: ConfigManager

    val clipboardChannel: Channel<suspend () -> Unit>

    suspend fun tryWriteClipboard(
        clipData: ClipData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
    )

    suspend fun tryWriteRemoteClipboard(clipData: ClipData)

    suspend fun tryWriteRemoteClipboardWithFile(clipData: ClipData)

    suspend fun clearRemoteClipboard(clipData: ClipData)
}
