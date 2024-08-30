package com.crosspaste.task.extra

import com.crosspaste.dao.task.ExecutionHistory
import com.crosspaste.dao.task.PasteTaskExtraInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("base")
class BaseExtraInfo : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()
}
