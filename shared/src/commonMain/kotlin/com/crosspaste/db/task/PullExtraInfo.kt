package com.crosspaste.db.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("pull")
class PullExtraInfo(
    @SerialName("id")
    val id: Long,
    // Carried to the relay scheduled once the files have landed
    @SerialName("seenAppInstanceIds")
    val seenAppInstanceIds: Set<String> = emptySet(),
    // Whether the landed paste also replaces the system clipboard. False for
    // background catch-up pulls and non-newest batch items; absent in tasks
    // stored by older versions, which always wrote it
    @SerialName("writePasteboard")
    val writePasteboard: Boolean = true,
    @SerialName("historyPull")
    val historyPull: Boolean = false,
) : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    @SerialName("pullChunks")
    var pullChunks: IntArray = intArrayOf()
}
