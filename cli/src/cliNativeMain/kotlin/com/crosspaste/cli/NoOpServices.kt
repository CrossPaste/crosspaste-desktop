package com.crosspaste.cli

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
