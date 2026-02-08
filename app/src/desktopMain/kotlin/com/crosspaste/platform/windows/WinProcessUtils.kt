package com.crosspaste.platform.windows

import io.github.oshai.kotlinlogging.KotlinLogging

object WinProcessUtils {

    private val GET_CHILD_PROCESS_IDS =
        arrayOf("wmic", "process", "where", "(ParentProcessId=%d)", "get", "Name,", "ProcessId")

    private val logger = KotlinLogging.logger {}

    fun getChildProcessIds(parentProcessId: Long): List<Pair<String, Long>> {
        val command = GET_CHILD_PROCESS_IDS.map { it.format(parentProcessId) }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        return try {
            val processIds = mutableListOf<Pair<String, Long>>()
            process.inputStream.bufferedReader().use { reader ->
                val lines = reader.readLines()
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
            }
            processIds
        } finally {
            process.destroyForcibly()
        }
    }

    fun killProcessSet(pids: Set<Long>) {
        for (pid in pids) {
            runCatching {
                // Build the taskkill command to forcefully terminate the specified PID
                val command = listOf("taskkill", "/F", "/PID", pid.toString())
                val process = ProcessBuilder(command).start()
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
