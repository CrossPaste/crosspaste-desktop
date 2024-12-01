package com.crosspaste.paste

import com.crosspaste.config.ConfigManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId
import kotlin.coroutines.cancellation.CancellationException

interface PasteboardService : PasteboardMonitor {

    val logger: KLogger

    var owner: Boolean

    val pasteRealm: PasteRealm

    val configManager: ConfigManager

    val pasteboardChannel: Channel<suspend () -> Unit>

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
