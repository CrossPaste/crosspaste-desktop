package com.clipevery.clip

import com.clipevery.dao.clip.ClipDao
import com.clipevery.os.macos.MacosClipboardService
import com.clipevery.os.windows.WindowsClipboardService
import com.clipevery.platform.currentPlatform
import com.clipevery.task.TaskExecutor

fun getDesktopClipboardService(clipDao: ClipDao,
                               taskExecutor: TaskExecutor,
                               clipConsumer: TransferableConsumer,
                               clipProducer: TransferableProducer): ClipboardService {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosClipboardService(clipDao, taskExecutor, clipConsumer, clipProducer)
    } else if (currentPlatform.isWindows()) {
        WindowsClipboardService(clipDao, taskExecutor, clipConsumer, clipProducer)
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}