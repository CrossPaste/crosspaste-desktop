package com.crosspaste.headless

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel

class HeadlessPasteboardService(
    override val configManager: CommonConfigManager,
    override val pasteDao: PasteDao,
) : PasteboardService {

    override val logger: KLogger = KotlinLogging.logger {}

    override var owner: Boolean = false

    override val remotePasteboardChannel: Channel<suspend () -> Result<Unit?>> = Channel(Channel.CONFLATED)

    override val serviceScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

    override fun start() {
        logger.info { "Headless pasteboard service started (no clipboard monitoring)" }
        startRemotePasteboardListener()
    }

    override fun stop() {
        logger.info { "Headless pasteboard service stopped" }
    }

    override fun toggle() {
        val enablePasteboardListening = configManager.getCurrentConfig().enablePasteboardListening
        if (enablePasteboardListening) {
            stop()
        } else {
            start()
        }
        configManager.updateConfig("enablePasteboardListening", !enablePasteboardListening)
    }

    override suspend fun tryWritePasteboard(
        id: Long?,
        pasteItem: PasteItem,
        localOnly: Boolean,
        updateCreateTime: Boolean,
    ): Result<Unit?> =
        runCatching {
            logger.info { "Headless mode: paste item stored to DB only (no system clipboard)" }
        }

    override suspend fun tryWritePasteboard(
        pasteData: PasteData,
        localOnly: Boolean,
        primary: Boolean,
        updateCreateTime: Boolean,
    ): Result<Unit?> =
        runCatching {
            logger.info { "Headless mode: paste data stored to DB only (no system clipboard)" }
        }

    override suspend fun tryWriteRemotePasteboard(pasteData: PasteData): Result<Unit?> =
        pasteDao.releaseRemotePasteData(pasteData) {
            remotePasteboardChannel.trySend {
                tryWritePasteboard(pasteData = it, localOnly = true)
            }
        }

    override suspend fun tryWriteRemotePasteboardWithFile(pasteData: PasteData): Result<Unit?> =
        pasteDao.releaseRemotePasteDataWithFile(pasteData.id) {
            remotePasteboardChannel.trySend {
                tryWritePasteboard(pasteData = it, localOnly = true)
            }
        }

    override suspend fun clearRemotePasteboard(pasteData: PasteData): Result<Unit?> =
        pasteDao.markDeletePasteData(pasteData.id)
}
