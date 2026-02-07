package com.crosspaste.paste

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
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

    val configManager: CommonConfigManager

    val remotePasteboardChannel: Channel<suspend () -> Result<Unit?>>

    val serviceScope: CoroutineScope

    fun startRemotePasteboardListener() {
        serviceScope.launch {
            var retryDelay = 1000L
            val maxRetryDelay = 30000L
            while (true) {
                runCatching {
                    for (task in remotePasteboardChannel) {
                        try {
                            task()
                            retryDelay = 1000L
                        } catch (e: Exception) {
                            logger.error(e) { "Run write remote pasteboard" }
                        }
                    }
                    return@launch
                }.onFailure { e ->
                    if (e is CancellationException) {
                        throw e
                    }
                    logger.error(e) { "Channel write remote failed, retrying in ${retryDelay}ms" }
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
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
