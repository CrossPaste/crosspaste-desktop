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
    ): TaskBuilder = addPullFileTask(id, remotePasteDataId, emptySet())

    fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
        seenAppInstanceIds: Set<String>,
    ): TaskBuilder = addPullFileTask(id, remotePasteDataId)

    fun addSyncTask(
        id: Long,
        fileSize: Long,
        appInstanceId: String,
        targetAppInstanceIds: Set<String>? = null,
    ): TaskBuilder

    fun addRelaySyncTask(
        id: Long,
        appInstanceId: String,
    ): TaskBuilder = addRelaySyncTask(id, appInstanceId, emptySet())

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
