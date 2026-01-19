package com.crosspaste.db.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("sync")
class SyncExtraInfo(
    @SerialName("appInstanceId")
    val appInstanceId: String,
) : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    @SerialName("syncFails")
    val syncFails: MutableSet<String> = mutableSetOf()
}
