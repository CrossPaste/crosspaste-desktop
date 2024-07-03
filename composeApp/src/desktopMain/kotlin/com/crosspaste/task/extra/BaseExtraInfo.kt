package com.crosspaste.task.extra

import com.crosspaste.dao.task.ClipTaskExtraInfo
import com.crosspaste.dao.task.ExecutionHistory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("base")
class BaseExtraInfo : ClipTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()
}
