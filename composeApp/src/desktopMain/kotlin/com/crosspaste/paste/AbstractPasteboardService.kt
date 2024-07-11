package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.Transferable

abstract class AbstractPasteboardService : PasteboardService, ClipboardOwner {

    abstract var ownerTransferable: Transferable?

    abstract val systemClipboard: Clipboard

    abstract val pasteConsumer: TransferableConsumer

    abstract val pasteProducer: TransferableProducer

    fun isValidContents(contents: Transferable?): Boolean {
        return contents != null && contents.transferDataFlavors.isNotEmpty()
    }

    fun getPasteboardContentsBySafe(): Transferable? {
        return try {
            systemClipboard.getContents(null)
        } catch (e: Exception) {
            logger.error(e) { "getContentsBySafe error" }
            null
        }
    }

    override suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean,
        filterFile: Boolean,
    ) {
        try {
            pasteProducer.produce(pasteData, localOnly, filterFile)?.let {
                it as DesktopWriteTransferable
                ownerTransferable = it
                owner = true
                systemClipboard.setContents(ownerTransferable, this)
            }
        } catch (e: Exception) {
            logger.error(e) { "tryWritePasteboard error" }
        }
    }

    override suspend fun tryWriteRemotePasteboard(pasteData: PasteData) {
        pasteDao.releaseRemotePasteData(pasteData) { storePasteData, filterFile ->
            pasteboardChannel.trySend { tryWritePasteboard(storePasteData, localOnly = true, filterFile = filterFile) }
        }
    }

    override suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData) {
        pasteDao.releaseRemotePasteDataWithFile(pasteData.id) { storePasteData ->
            pasteboardChannel.trySend { tryWritePasteboard(storePasteData, localOnly = true, filterFile = false) }
        }
    }

    override suspend fun clearRemotePasteboard(pasteData: PasteData) {
        pasteDao.markDeletePasteData(pasteData.id)
    }

    @Synchronized
    override fun toggle() {
        val enablePasteboardListening = configManager.config.enablePasteboardListening
        if (enablePasteboardListening) {
            stop()
        } else {
            start()
        }
        configManager.updateConfig("enablePasteboardListening", !enablePasteboardListening)
    }
}
