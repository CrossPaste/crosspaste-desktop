package com.crosspaste.db.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("sync")
class SyncExtraInfo(
    @SerialName("appInstanceId")
    val appInstanceId: String,
    @SerialName("targetAppInstanceIds")
    val targetAppInstanceIds: Set<String>? = null,
    // Devices that already hold the paste; a relay never forwards to them
    @SerialName("seenAppInstanceIds")
    val seenAppInstanceIds: Set<String> = emptySet(),
) : PasteTaskExtraInfo {

    @SerialName("executionHistories")
    override val executionHistories: MutableList<ExecutionHistory> = mutableListOf()

    @SerialName("syncFails")
    val syncFails: MutableSet<String> = mutableSetOf()
}
