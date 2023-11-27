package com.clipevery.macos

import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.macos.api.MacosApi
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MacosClipboardService
    (override val clipConsumer: TransferableConsumer) : ClipboardService {

    private var executor: ScheduledExecutorService? = null

    private var changeCount = 0

    private var systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override fun run() {
        try {
            MacosApi.INSTANCE.getClipboardChangeCount().let { currentChangeCount ->
                if (changeCount == currentChangeCount) {
                    return
                }
                changeCount = currentChangeCount
                val contents: Transferable? = systemClipboard.getContents(null)
                contents?.let {
                    clipConsumer.consume(it)
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun start() {
        if (executor?.isShutdown != false) {
            executor = Executors.newScheduledThreadPool(2) { r -> Thread(r, "Clipboard Monitor") }
        }
        executor?.scheduleAtFixedRate(this, 0, 300, TimeUnit.MILLISECONDS)
    }

    override fun stop() {
        executor?.let {
            it.shutdown()

            try {
                if (!it.awaitTermination(600, TimeUnit.MICROSECONDS)) {
                    it.shutdownNow()
                    if (!it.awaitTermination(600, TimeUnit.MICROSECONDS)) {
                        println("task did not terminate")
                    }
                }
                println("stop ")
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                it.shutdownNow()
            }
        }
    }
}