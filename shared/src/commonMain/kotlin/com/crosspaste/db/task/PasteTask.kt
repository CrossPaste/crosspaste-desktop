package com.crosspaste.db.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PasteTask(
    @SerialName("taskId")
    val taskId: Long,
    @SerialName("pasteDataId")
    val pasteDataId: Long?,
    @SerialName("taskType")
    val taskType: Int = TaskType.UNKNOWN_TASK,
    @SerialName("status")
    val status: Int = TaskStatus.PREPARING,
    @SerialName("createTime")
    val createTime: Long,
    @SerialName("modifyTime")
    val modifyTime: Long,
    @SerialName("extraInfo")
    val extraInfo: String,
)
