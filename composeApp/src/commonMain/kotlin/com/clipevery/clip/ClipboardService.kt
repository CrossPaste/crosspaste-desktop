package com.clipevery.clip

import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.channels.Channel
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.Transferable

interface ClipboardService : ClipboardMonitor, ClipboardOwner {

    val logger: KLogger

    var owner: Boolean

    var ownerTransferable: Transferable?

    val systemClipboard: Clipboard

    val clipDao: ClipDao

    val configManager: ConfigManager

    val clipConsumer: TransferableConsumer

    val clipProducer: TransferableProducer

    val clipboardChannel: Channel<suspend () -> Unit>

    suspend fun tryWriteClipboard(
        clipData: ClipData,
        localOnly: Boolean = false,
        filterFile: Boolean = false,
    ) {
        try {
            clipProducer.produce(clipData, localOnly, filterFile)?.let {
                ownerTransferable = it
                owner = true
                systemClipboard.setContents(ownerTransferable, this)
            }
        } catch (e: Exception) {
            logger.error(e) { "tryWriteClipboard error" }
        }
    }

    suspend fun tryWriteRemoteClipboard(clipData: ClipData) {
        clipDao.releaseRemoteClipData(clipData) { storeClipData, filterFile ->
            clipboardChannel.trySend { tryWriteClipboard(storeClipData, localOnly = true, filterFile = filterFile) }
        }
    }

    suspend fun tryWriteRemoteClipboardWithFile(clipData: ClipData) {
        clipDao.releaseRemoteClipDataWithFile(clipData.id) { storeClipData ->
            clipboardChannel.trySend { tryWriteClipboard(storeClipData, localOnly = true, filterFile = false) }
        }
    }

    suspend fun clearRemoteClipboard(clipData: ClipData) {
        clipDao.markDeleteClipData(clipData.id)
    }
}
