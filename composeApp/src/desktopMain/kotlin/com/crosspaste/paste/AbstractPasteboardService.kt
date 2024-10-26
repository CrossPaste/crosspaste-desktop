package com.crosspaste.paste

import com.crosspaste.app.AppWindowManager
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.sound.SoundService
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import org.mongodb.kbson.ObjectId
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.Transferable

abstract class AbstractPasteboardService : PasteboardService, ClipboardOwner {

    abstract var ownerTransferable: Transferable?

    abstract val systemClipboard: Clipboard

    abstract val appWindowManager: AppWindowManager

    abstract val pasteConsumer: TransferableConsumer

    abstract val pasteProducer: TransferableProducer

    abstract val soundService: SoundService

    abstract val currentPaste: CurrentPaste

    override val pasteboardChannel: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)

    override val serviceScope = CoroutineScope(ioDispatcher + SupervisorJob())

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
        id: ObjectId,
        pasteItem: PasteItem,
        localOnly: Boolean,
        filterFile: Boolean,
        updateCreateTime: Boolean,
    ) {
        try {
            pasteProducer.produce(pasteItem, localOnly, filterFile)?.let {
                it as DesktopWriteTransferable
                systemClipboard.setContents(it, this)
                ownerTransferable = it
                owner = true
                currentPaste.setPasteId(id, updateCreateTime)
            }
        } catch (e: Exception) {
            logger.error(e) { "tryWritePasteboard error" }
        }
    }

    override suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean,
        filterFile: Boolean,
        primary: Boolean,
        updateCreateTime: Boolean,
    ) {
        try {
            pasteProducer.produce(pasteData, localOnly, filterFile, primary)?.let {
                it as DesktopWriteTransferable
                systemClipboard.setContents(it, this)
                ownerTransferable = it
                owner = true
                currentPaste.setPasteId(pasteData.id, updateCreateTime)
            }
        } catch (e: Exception) {
            logger.error(e) { "tryWritePasteboard error" }
        }
    }

    override suspend fun tryWriteRemotePasteboard(pasteData: PasteData) {
        pasteRealm.releaseRemotePasteData(pasteData) { storePasteData, filterFile ->
            pasteboardChannel.trySend { tryWritePasteboard(storePasteData, localOnly = true, filterFile = filterFile) }
        }
    }

    override suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData) {
        pasteRealm.releaseRemotePasteDataWithFile(pasteData.id) { storePasteData ->
            pasteboardChannel.trySend { tryWritePasteboard(storePasteData, localOnly = true, filterFile = false) }
        }
    }

    override suspend fun clearRemotePasteboard(pasteData: PasteData) {
        pasteRealm.markDeletePasteData(pasteData.id)
    }

    @Synchronized
    override fun toggle() {
        val enablePasteboardListening = configManager.config.enablePasteboardListening
        if (enablePasteboardListening) {
            stop()
            soundService.disablePasteboardListening()
        } else {
            start()
            soundService.enablePasteboardListening()
        }
        configManager.updateConfig("enablePasteboardListening", !enablePasteboardListening)
    }
}
