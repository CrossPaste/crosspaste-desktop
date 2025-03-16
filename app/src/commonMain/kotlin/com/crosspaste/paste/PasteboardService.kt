package com.crosspaste.paste

import com.crosspaste.config.ConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteItem
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

interface PasteboardService : PasteboardMonitor {

    val logger: KLogger

    var owner: Boolean

    val pasteDao: PasteDao

    val configManager: ConfigManager

    val pasteboardChannel: Channel<suspend () -> Result<Unit>>

    val serviceScope: CoroutineScope

    fun startRemotePasteboardListener() {
        serviceScope.launch {
            try {
                for (task in pasteboardChannel) {
                    try {
                        task()
                    } catch (e: Exception) {
                        logger.error(e) { "Run write remote pasteboard" }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Channel write remote failed" }
                delay(1000)
                startRemotePasteboardListener()
            }
        }
    }

    suspend fun tryWritePasteboard(
        id: Long,
        pasteItem: PasteItem,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
        updateCreateTime: Boolean = false,
    ): Result<Unit>

    suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
        primary: Boolean = false,
        updateCreateTime: Boolean = false,
    ): Result<Unit>

    suspend fun tryWriteRemotePasteboard(pasteData: PasteData): Result<Unit>

    suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData): Result<Unit>

    suspend fun clearRemotePasteboard(pasteData: PasteData): Result<Unit>
}
