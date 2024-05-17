package com.clipevery.os.linux

import com.clipevery.app.AppWindowManager
import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.TransferableProducer
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable

class LinuxClipboardService(
    override val appWindowManager: AppWindowManager,
    override val clipDao: ClipDao,
    override val configManager: ConfigManager,
    override val clipConsumer: TransferableConsumer,
    override val clipProducer: TransferableProducer,
) : ClipboardService {
    override val logger: KLogger = KotlinLogging.logger {}

    override var owner: Boolean = false

    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override val clipboardChannel: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)

    override fun start() {
    }

    override fun stop() {
    }

    override fun toggle() {
        val enableClipboardListening = configManager.config.enableClipboardListening
        if (enableClipboardListening) {
            stop()
        } else {
            start()
        }
        configManager.updateConfig { it.copy(enableClipboardListening = !enableClipboardListening) }
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        owner = false
    }
}
