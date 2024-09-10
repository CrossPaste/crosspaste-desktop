package com.crosspaste.task.extra

import com.crosspaste.realm.task.ExecutionHistory
import com.crosspaste.realm.task.PasteTaskExtraInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("base")
class BaseExtraInfo : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()
}
