package com.crosspaste.db.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("delayedDelete")
class DelayedDeleteExtraInfo(
    val executeAfter: Long,
) : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()
}
