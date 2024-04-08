package com.clipevery.dao.task

import kotlinx.serialization.Serializable

interface ClipTaskExtraInfo {

    val executionHistories: MutableList<ExecutionHistory>
}

@Serializable
data class ExecutionHistory(val startTime: Long, val endTime: Long, val status: Int, val message: String?)
