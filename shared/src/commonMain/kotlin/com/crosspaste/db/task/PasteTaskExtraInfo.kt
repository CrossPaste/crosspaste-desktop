package com.crosspaste.db.task

import kotlinx.serialization.Serializable

@Serializable
sealed interface PasteTaskExtraInfo {

    val executionHistories: MutableList<ExecutionHistory>
}

@Serializable
data class ExecutionHistory(
    val startTime: Long,
    val endTime: Long,
    val status: Int,
    val message: String?,
)
