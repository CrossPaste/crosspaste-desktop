package com.clipevery.task.extra

import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.dao.task.ExecutionHistory
import kotlinx.serialization.Serializable

@Serializable
class BaseExtraInfo: ClipTaskExtraInfo {

    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()
}