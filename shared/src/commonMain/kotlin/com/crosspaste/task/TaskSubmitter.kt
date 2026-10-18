package com.crosspaste.task

import com.crosspaste.paste.PasteType

interface TaskSubmitter {

    suspend fun submit(block: suspend TaskBuilder.() -> Unit)
}

interface TaskBuilder {

    fun addDelayedDeletePasteTask(
        id: Long,
        delayMillis: Long,
    ): TaskBuilder

    fun addDeletePasteTasks(ids: List<Long>): TaskBuilder

    fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
    ): TaskBuilder

    // Defaults to dropping the set so implementations predating RelaySeen still compile
    fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
        seenAppInstanceIds: Set<String>,
    ): TaskBuilder = addPullFileTask(id, remotePasteDataId)

    // Defaults to dropping the flag so implementations predating it still compile
    fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
        seenAppInstanceIds: Set<String>,
        writePasteboard: Boolean,
    ): TaskBuilder = addPullFileTask(id, remotePasteDataId, seenAppInstanceIds)

    // Older platform implementations retain their existing task behavior until ported.
    fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
        seenAppInstanceIds: Set<String>,
        writePasteboard: Boolean,
        historyPull: Boolean,
    ): TaskBuilder = addPullFileTask(id, remotePasteDataId, seenAppInstanceIds, writePasteboard)

    fun addSyncTask(
        id: Long,
        fileSize: Long,
        appInstanceId: String,
        targetAppInstanceIds: Set<String>? = null,
    ): TaskBuilder

    fun addRelaySyncTask(
        id: Long,
        appInstanceId: String,
    ): TaskBuilder

    // Defaults to dropping the set so implementations predating RelaySeen still compile
    fun addRelaySyncTask(
        id: Long,
        appInstanceId: String,
        seenAppInstanceIds: Set<String>,
    ): TaskBuilder = addRelaySyncTask(id, appInstanceId)

    fun addPullIconTask(
        id: Long,
        existIconFile: Boolean,
    ): TaskBuilder

    fun addRenderingTask(
        id: Long,
        pasteType: PasteType,
    ): TaskBuilder
}
