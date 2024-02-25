package com.clipevery.os.macos

import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.utils.cpuDispatcher
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard

class MacosClipboardService(override val clipConsumer: TransferableConsumer): ClipboardService {

    private val logger = KotlinLogging.logger {}

    private var changeCount = 0

    private var systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    private var job: Job? = null

    override fun run(): Unit = runBlocking {
        launch {
            while (isActive) {
                try {
                    MacosApi.INSTANCE.getClipboardChangeCount().let { currentChangeCount ->
                        if (changeCount != currentChangeCount) {
                            changeCount = currentChangeCount
                            val contents = systemClipboard.getContents(null)
                            contents?.let {
                                withContext(ioDispatcher) {
                                    clipConsumer.consume(it)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to consume transferable" }
                }
                delay(300L)
            }
        }
    }

    override fun start() {
        if (job?.isActive != true) {
            job = CoroutineScope(cpuDispatcher).launch {
                run()
            }
        }
    }

    override fun stop() {
        job?.cancel()
    }
}