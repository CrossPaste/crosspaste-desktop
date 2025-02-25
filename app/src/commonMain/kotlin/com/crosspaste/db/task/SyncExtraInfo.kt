package com.crosspaste.db.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("sync")
class SyncExtraInfo : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    @SerialName("syncFails")
    val syncFails: MutableSet<String> = mutableSetOf()
}
