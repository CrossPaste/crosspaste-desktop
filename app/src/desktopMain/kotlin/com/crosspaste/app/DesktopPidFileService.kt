package com.crosspaste.app

import com.crosspaste.path.AppPathProvider
import com.crosspaste.presist.FilePersist
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path

class DesktopPidFileService(
    private val appPathProvider: AppPathProvider,
    private val appExitService: AppExitService,
) {

    private val logger = KotlinLogging.logger {}

    val pidFilePath: Path
        get() = appPathProvider.pasteUserPath.resolve("crosspaste.pid")

    fun start() {
        val pid = ProcessHandle.current().pid().toString()
        FilePersist
            .createOneFilePersist(pidFilePath)
            .saveBytes(pid.encodeToByteArray())
        appExitService.beforeExitList.add {
            runCatching { pidFilePath.toFile().delete() }
        }
        logger.info { "PID file written: $pidFilePath (pid=$pid)" }
    }
}
