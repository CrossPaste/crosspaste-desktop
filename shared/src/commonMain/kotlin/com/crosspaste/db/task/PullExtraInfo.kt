package com.crosspaste.db.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("pull")
class PullExtraInfo(
    @SerialName("id")
    val id: Long,
) : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    @SerialName("pullChunks")
    var pullChunks: IntArray = intArrayOf()
}
