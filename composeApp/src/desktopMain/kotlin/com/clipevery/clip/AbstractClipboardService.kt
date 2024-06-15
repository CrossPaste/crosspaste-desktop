package com.clipevery.clip

import com.clipevery.dao.clip.ClipData
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable

abstract class AbstractClipboardService : ClipboardService {

    abstract var ownerTransferable: Transferable?

    abstract val systemClipboard: Clipboard

    abstract val clipConsumer: TransferableConsumer

    abstract val clipProducer: TransferableProducer

    fun isValidContents(contents: Transferable?): Boolean {
        return contents != null && contents.transferDataFlavors.isNotEmpty()
    }

    fun getClipboardContentsBySafe(): Transferable? {
        return try {
            systemClipboard.getContents(null)
        } catch (e: Exception) {
            logger.error(e) { "getContentsBySafe error" }
            null
        }
    }

    override suspend fun tryWriteClipboard(
        clipData: ClipData,
        localOnly: Boolean,
        filterFile: Boolean,
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

    override suspend fun tryWriteRemoteClipboard(clipData: ClipData) {
        clipDao.releaseRemoteClipData(clipData) { storeClipData, filterFile ->
            clipboardChannel.trySend { tryWriteClipboard(storeClipData, localOnly = true, filterFile = filterFile) }
        }
    }

    override suspend fun tryWriteRemoteClipboardWithFile(clipData: ClipData) {
        clipDao.releaseRemoteClipDataWithFile(clipData.id) { storeClipData ->
            clipboardChannel.trySend { tryWriteClipboard(storeClipData, localOnly = true, filterFile = false) }
        }
    }

    override suspend fun clearRemoteClipboard(clipData: ClipData) {
        clipDao.markDeleteClipData(clipData.id)
    }

    @Synchronized
    override fun toggle() {
        val enableClipboardListening = configManager.config.enableClipboardListening
        if (enableClipboardListening) {
            stop()
        } else {
            start()
        }
        configManager.updateConfig { it.copy(enableClipboardListening = !enableClipboardListening) }
    }
}
