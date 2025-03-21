package com.crosspaste.platform.windows

import io.github.oshai.kotlinlogging.KotlinLogging

object WinProcessUtils {

    private const val GET_CHILD_PROCESS_IDS = "wmic process where (ParentProcessId=%d) get Name, ProcessId"

    private val logger = KotlinLogging.logger {}

    fun getChildProcessIds(parentProcessId: Long): List<Pair<String, Long>> {
        val process = Runtime.getRuntime().exec(GET_CHILD_PROCESS_IDS.format(parentProcessId))
        val reader = process.inputStream.bufferedReader()
        val lines = reader.readLines()
        val processIds = mutableListOf<Pair<String, Long>>()
        for (line in lines) {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size == 2) {
                val processName = parts[0]
                val processId = parts[1].toLongOrNull()
                if (processId != null) {
                    processIds.add(processName to processId)
                }
            }
        }
        return processIds
    }

    fun killProcessSet(pids: Set<Long>) {
        for (pid in pids) {
            runCatching {
                // Build the taskkill command to forcefully terminate the specified PID
                val command = "taskkill /F /PID $pid"
                val process = Runtime.getRuntime().exec(command)
                val exitValue = process.waitFor()
                if (exitValue == 0) {
                    logger.info { "Successfully terminated process PID: $pid" }
                } else {
                    logger.error { "Failed to terminate process PID: $pid" }
                }
            }.onFailure { e ->
                when (e) {
                    is InterruptedException -> logger.error(e) { "Error trying to terminate process PID: $pid" }
                    else -> logger.error(e) { "Error trying to terminate process PID: $pid" }
                }
            }
        }
    }
}
