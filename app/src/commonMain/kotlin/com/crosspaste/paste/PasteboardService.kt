package com.crosspaste.paste

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.PasteItem
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

interface PasteboardService : PasteboardMonitor {

    val logger: KLogger

    var owner: Boolean

    val pasteDao: PasteDao

    val configManager: CommonConfigManager

    val remotePasteboardChannel: Channel<suspend () -> Result<Unit?>>

    val serviceScope: CoroutineScope

    fun startRemotePasteboardListener() {
        serviceScope.launch {
            for (task in remotePasteboardChannel) {
                try {
                    if (configManager.getCurrentConfig().enablePasteboardListening) {
                        task()
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Run write remote pasteboard" }
                }
            }
        }
    }

    suspend fun tryWritePasteboard(
        id: Long? = null,
        pasteItem: PasteItem,
        localOnly: Boolean = false,
        updateCreateTime: Boolean = false,
    ): Result<Unit?>

    suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean = false,
        primary: Boolean = configManager.getCurrentConfig().pastePrimaryTypeOnly,
        updateCreateTime: Boolean = false,
    ): Result<Unit?>

    suspend fun tryWriteRemotePasteboard(pasteData: PasteData): Result<Unit?>

    suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData): Result<Unit?>

    suspend fun clearRemotePasteboard(pasteData: PasteData): Result<Unit?>
}
