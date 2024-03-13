package com.clipevery.utils

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.dao.task.ExecutionHistory
import com.clipevery.dao.task.TaskStatus
import com.clipevery.task.extra.BaseExtraInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object TaskUtils {

    fun createTask(clipId: Int, taskType: Int, extraInfo: ClipTaskExtraInfo = BaseExtraInfo()): ClipTask {
        return ClipTask().apply {
            this.clipId = clipId
            this.taskType = taskType
            this.status = TaskStatus.PREPARING
            this.createTime = System.currentTimeMillis()
            this.modifyTime = System.currentTimeMillis()
            this.extraInfo = JsonUtils.JSON.encodeToString(extraInfo)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: ClipTaskExtraInfo> getExtraInfo(clipTask: ClipTask, kclass: KClass<T>): T {
        val serializer = Json.serializersModule.serializer(kclass.java)
        return JsonUtils.JSON.decodeFromString(serializer, clipTask.extraInfo) as T
    }

    fun unexpectError(clipTask: ClipTask, e: Throwable) {
        clipTask.status = TaskStatus.FAILURE
        val currentTime = System.currentTimeMillis()
        val executionHistory = ExecutionHistory(startTime = clipTask.modifyTime,
            endTime = currentTime,
            status = TaskStatus.FAILURE,
            message = e.message ?: "Unknown error")
        val clipTaskExtraInfo = JsonUtils.JSON.decodeFromString<BaseExtraInfo>(clipTask.extraInfo)
        clipTaskExtraInfo.executionHistories.add(executionHistory)
        clipTask.extraInfo = JsonUtils.JSON.encodeToString(clipTaskExtraInfo)
    }
}