package com.clipevery.os.linux

import com.clipevery.app.AppWindowManager
import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.TransferableProducer
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.channels.Channel
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable

class LinuxClipboardService(
    override val appWindowManager: AppWindowManager,
    override val clipDao: ClipDao,
    override val configManager: ConfigManager,
    override val clipConsumer: TransferableConsumer,
    override val clipProducer: TransferableProducer,
) : ClipboardService {
    override val logger: KLogger
        get() = TODO("Not yet implemented")
    override var owner: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override var ownerTransferable: Transferable?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val systemClipboard: Clipboard
        get() = TODO("Not yet implemented")
    override val clipboardChannel: Channel<suspend () -> Unit>
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun toggle() {
        TODO("Not yet implemented")
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        TODO("Not yet implemented")
    }
}
