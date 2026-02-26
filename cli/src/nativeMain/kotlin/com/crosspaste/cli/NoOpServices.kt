package com.crosspaste.cli

import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter

class NoOpSearchContentService : SearchContentService {

    override fun createSearchContent(
        source: String?,
        searchContentList: List<String>,
    ): String = (listOfNotNull(source) + searchContentList).joinToString(" ")

    override fun createSearchTerms(queryString: String): List<String> =
        queryString.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
}

class NoOpTaskSubmitter : TaskSubmitter {

    override suspend fun submit(block: suspend TaskBuilder.() -> Unit) {
        // CLI is read-only, no background tasks
    }
}

private class NoOpTaskBuilder : TaskBuilder {
    override fun addDelayedDeletePasteTask(
        id: Long,
        delayMillis: Long,
    ) = this

    override fun addDeletePasteTasks(ids: List<Long>) = this

    override fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
    ) = this

    override fun addSyncTask(
        id: Long,
        appInstanceId: String,
        fileSize: Long,
    ) = this

    override fun addRelaySyncTask(
        id: Long,
        appInstanceId: String,
    ) = this

    override fun addPullIconTask(
        id: Long,
        existIconFile: Boolean,
    ) = this

    override fun addRenderingTask(
        id: Long,
        pasteType: PasteType,
    ) = this
}
