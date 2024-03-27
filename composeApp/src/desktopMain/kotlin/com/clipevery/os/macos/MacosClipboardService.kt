package com.clipevery.os.macos

import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.TransferableProducer
import com.clipevery.dao.clip.ClipDao
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.task.TaskExecutor
import com.clipevery.utils.cpuDispatcher
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable

class MacosClipboardService(override val clipDao: ClipDao,
                            override val taskExecutor: TaskExecutor,
                            override val clipConsumer: TransferableConsumer,
                            override val clipProducer: TransferableProducer): ClipboardService {

    private val logger = KotlinLogging.logger {}

    private var changeCount = 0

    @Volatile
    override var owner = false

    @Volatile
    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    private val serviceScope = CoroutineScope(cpuDispatcher + SupervisorJob())

    private var job: Job? = null

    private fun run(): Job {
        return serviceScope.launch(CoroutineName("MacClipboardService")) {
            while (isActive) {
                try {
                    val isRemote = IntByReference()
                    val isClipevery = IntByReference()
                    MacosApi.INSTANCE.getClipboardChangeCount(changeCount, isRemote, isClipevery)
                        .let { currentChangeCount ->
                            if (changeCount != currentChangeCount) {
                                changeCount = currentChangeCount
                                if (isClipevery.value != 0) {
                                    logger.debug { "Ignoring clipevery change" }
                                } else {
                                    delay(20L)
                                    val contents = systemClipboard.getContents(null)
                                    if (contents != ownerTransferable) {
                                        contents?.let {
                                            launch(CoroutineName("MacClipboardServiceConsumer")) {
                                                clipConsumer.consume(it, isRemote.value != 0)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to consume transferable" }
                }
                delay(280L)
            }
        }
    }


    override fun lostOwnership(clipboard: Clipboard?, contents: Transferable?) {
        owner = false
    }

    override fun start() {
        if (job?.isActive != true) {
            job = run()
        }
    }

    override fun stop() {
        job?.cancel()
    }
}