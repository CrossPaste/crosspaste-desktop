package com.crosspaste.task.extra

import com.crosspaste.realm.task.ExecutionHistory
import com.crosspaste.realm.task.PasteTaskExtraInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("pull")
class PullExtraInfo : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    @SerialName("pullChunks")
    var pullChunks: IntArray = intArrayOf()
}
