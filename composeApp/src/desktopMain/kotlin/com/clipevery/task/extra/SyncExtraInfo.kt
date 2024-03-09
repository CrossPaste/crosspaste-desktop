package com.clipevery.task.extra

import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.dao.task.ExecutionHistory
import kotlinx.serialization.Serializable

@Serializable
class SyncExtraInfo: ClipTaskExtraInfo {

    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    val syncFails: MutableSet<String> = mutableSetOf()
}