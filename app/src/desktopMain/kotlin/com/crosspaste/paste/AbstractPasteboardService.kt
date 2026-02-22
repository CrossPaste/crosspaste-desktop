package com.crosspaste.paste

import com.crosspaste.app.AppWindowManager
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.sound.SoundService
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.Transferable

abstract class AbstractPasteboardService :
    PasteboardService,
    ClipboardOwner {

    abstract var ownerTransferable: Transferable?

    abstract val systemClipboard: Clipboard

    abstract val appWindowManager: AppWindowManager

    abstract val notificationManager: NotificationManager

    abstract val pasteConsumer: TransferableConsumer

    abstract val pasteProducer: TransferableProducer

    abstract val soundService: SoundService

    abstract val currentPaste: CurrentPaste

    abstract val sourceExclusionService: SourceExclusionService

    override val remotePasteboardChannel: Channel<suspend () -> Result<Unit?>> = Channel(Channel.CONFLATED)

    override val serviceScope = CoroutineScope(ioDispatcher + SupervisorJob())

    fun isValidContents(contents: Transferable?): Boolean =
        contents != null && contents.transferDataFlavors.isNotEmpty()

    fun getPasteboardContentsBySafe(): Transferable? =
        runCatching {
            systemClipboard.getContents(null)
        }.onFailure { e ->
            logger.error(e) { "getContentsBySafe error" }
        }.getOrNull()

    override suspend fun tryWritePasteboard(
        id: Long?,
        pasteItem: PasteItem,
        localOnly: Boolean,
        updateCreateTime: Boolean,
    ): Result<Unit?> =
        runCatching {
            pasteProducer.produce(pasteItem, localOnly)?.let {
                it as DesktopWriteTransferable
                writePasteboard(pasteItem, it)
                ownerTransferable = it
                owner = true
                id?.let {
                    currentPaste.setPasteId(id, updateCreateTime)
                }
            }
        }.onFailure { e ->
            logger.error(e) { "tryWritePasteboard error" }
        }

    override suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean,
        primary: Boolean,
        updateCreateTime: Boolean,
    ): Result<Unit?> =
        runCatching {
            pasteProducer.produce(pasteData, localOnly, primary)?.let {
                it as DesktopWriteTransferable
                writePasteboard(pasteData, it)
                ownerTransferable = it
                owner = true
                currentPaste.setPasteId(pasteData.id, updateCreateTime)
            }
        }.onFailure { e ->
            logger.error(e) { "tryWritePasteboard error" }
        }

    open fun writePasteboard(
        pasteItem: PasteItem,
        transferable: DesktopWriteTransferable,
    ) {
        systemClipboard.setContents(transferable, this)
    }

    open fun writePasteboard(
        pasteData: PasteData,
        transferable: DesktopWriteTransferable,
    ) {
        systemClipboard.setContents(transferable, this)
    }

    override suspend fun tryWriteRemotePasteboard(pasteData: PasteData): Result<Unit?> =
        pasteDao.releaseRemotePasteData(pasteData) { storePasteData ->
            remotePasteboardChannel
                .trySend {
                    tryWritePasteboard(
                        pasteData = storePasteData,
                        localOnly = true,
                    ).onFailure {
                        notificationManager.sendNotification(
                            title = { copyWriter -> copyWriter.getText("copy_failed") },
                            message = it.message?.let { message -> { message } },
                            messageType = MessageType.Error,
                        )
                    }
                }.onFailure { e ->
                    logger.error(e) { "Failed to send remote pasteboard task to channel" }
                }
        }

    override suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData): Result<Unit?> =
        pasteDao.releaseRemotePasteDataWithFile(pasteData.id) { storePasteData ->
            remotePasteboardChannel
                .trySend {
                    tryWritePasteboard(
                        pasteData = storePasteData,
                        localOnly = true,
                    ).onFailure {
                        notificationManager.sendNotification(
                            title = { copyWriter -> copyWriter.getText("copy_failed") },
                            message = it.message?.let { message -> { message } },
                            messageType = MessageType.Error,
                        )
                    }
                }.onFailure { e ->
                    logger.error(e) { "Failed to send remote pasteboard task to channel" }
                }
        }

    override suspend fun clearRemotePasteboard(pasteData: PasteData): Result<Unit?> =
        pasteDao.markDeletePasteData(pasteData.id)

    @Synchronized
    override fun toggle() {
        val enablePasteboardListening = configManager.getCurrentConfig().enablePasteboardListening
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
