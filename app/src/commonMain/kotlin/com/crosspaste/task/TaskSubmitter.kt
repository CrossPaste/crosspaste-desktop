package com.crosspaste.task

import com.crosspaste.paste.PasteType

interface TaskSubmitter {

    suspend fun submit(block: suspend TaskBuilder.() -> Unit)
}

interface TaskBuilder {

    fun addDeletePasteTasks(ids: List<Long>): TaskBuilder

    fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
    ): TaskBuilder

    fun addSyncTask(
        id: Long,
        appInstanceId: String,
        fileSize: Long,
    ): TaskBuilder

    fun addRelaySyncTask(
        id: Long,
        appInstanceId: String,
    ): TaskBuilder

    fun addPullIconTask(
        id: Long,
        existIconFile: Boolean,
    ): TaskBuilder

    fun addRenderingTask(
        id: Long,
        pasteType: PasteType,
    ): TaskBuilder
}
