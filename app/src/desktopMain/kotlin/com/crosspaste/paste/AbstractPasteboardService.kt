package com.crosspaste.paste

import com.crosspaste.app.AppWindowManager
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.sound.SoundService
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
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

    abstract val sourceExclusionService: DesktopSourceExclusionService

    override val remotePasteboardChannel: Channel<suspend () -> Result<Unit?>> = Channel(Channel.CONFLATED)

    override val serviceScope = namedScope(ioDispatcher, "AbstractPasteboardService")

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
        scope: PasteWriteScope,
        updateCreateTime: Boolean,
    ): Result<Unit?> =
        runCatching {
            pasteProducer.produce(pasteData, localOnly, scope)?.let {
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
        tryWriteRemotePasteboard(pasteData, emptySet())

    override suspend fun tryWriteRemotePasteboard(
        pasteData: PasteData,
        seenAppInstanceIds: Set<String>,
    ): Result<Unit?> =
        pasteReleaseService.releaseRemotePasteData(
            pasteData,
            seenAppInstanceIds,
            tryWritePasteboard = ::enqueueRemotePaste,
        )

    private fun enqueueRemotePaste(storePasteData: PasteData) {
        remotePasteboardChannel
            .trySend {
                tryWritePasteboard(
                    pasteData = storePasteData,
                    localOnly = true,
                ).onFailure {
                    notificationManager.sendNotification(
                        title = { copyWriter -> copyWriter.getText("copy_failed") },
                        messageType = MessageType.Error,
                    )
                }
            }.onFailure { e ->
                logger.error(e) { "Failed to send remote pasteboard task to channel" }
            }
    }

    override suspend fun tryWriteRemotePasteboardList(
        pasteDataList: List<PasteData>,
        writeToPasteboard: Boolean,
    ): Result<Unit?> =
        pasteReleaseService.releaseRemotePasteDataList(pasteDataList, historyPull = true) {
            pasteReleaseService.releaseRemotePasteData(
                it,
                writePasteboard = writeToPasteboard,
                historyPull = true,
            ) { paste ->
                if (writeToPasteboard) enqueueRemotePaste(paste)
            }
        }

    override suspend fun tryWriteRemotePasteboardWithFile(pasteId: Long): Result<Unit?> =
        tryWriteRemotePasteboardWithFile(pasteId, emptySet(), writeToPasteboard = true)

    override suspend fun tryWriteRemotePasteboardWithFile(
        pasteId: Long,
        seenAppInstanceIds: Set<String>,
    ): Result<Unit?> = tryWriteRemotePasteboardWithFile(pasteId, seenAppInstanceIds, writeToPasteboard = true)

    override suspend fun tryWriteRemotePasteboardWithFile(
        pasteId: Long,
        seenAppInstanceIds: Set<String>,
        writeToPasteboard: Boolean,
    ): Result<Unit?> =
        tryWriteRemotePasteboardWithFile(pasteId, seenAppInstanceIds, writeToPasteboard, historyPull = false)

    override suspend fun tryWriteRemotePasteboardWithFile(
        pasteId: Long,
        seenAppInstanceIds: Set<String>,
        writeToPasteboard: Boolean,
        historyPull: Boolean,
    ): Result<Unit?> =
        pasteReleaseService.releaseRemotePasteDataWithFile(pasteId, seenAppInstanceIds, historyPull) { storePasteData ->
            if (writeToPasteboard) {
                remotePasteboardChannel
                    .trySend {
                        tryWritePasteboard(
                            pasteData = storePasteData,
                            localOnly = true,
                        ).onFailure {
                            notificationManager.sendNotification(
                                title = { copyWriter -> copyWriter.getText("copy_failed") },
                                messageType = MessageType.Error,
                            )
                        }
                    }.onFailure { e ->
                        logger.error(e) { "Failed to send remote pasteboard task to channel" }
                    }
            }
        }

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
