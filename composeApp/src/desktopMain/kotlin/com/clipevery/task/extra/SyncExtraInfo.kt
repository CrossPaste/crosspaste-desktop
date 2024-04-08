package com.clipevery.task.extra

import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.dao.task.ExecutionHistory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("sync")
class SyncExtraInfo : ClipTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    @SerialName("syncFails")
    val syncFails: MutableSet<String> = mutableSetOf()
}
