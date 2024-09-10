package com.crosspaste.realm.task

import kotlinx.serialization.Serializable

interface PasteTaskExtraInfo {

    val executionHistories: MutableList<ExecutionHistory>
}

@Serializable
data class ExecutionHistory(val startTime: Long, val endTime: Long, val status: Int, val message: String?)
