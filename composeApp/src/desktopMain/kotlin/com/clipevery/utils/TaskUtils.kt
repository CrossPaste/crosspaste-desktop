package com.clipevery.utils

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.dao.task.ExecutionHistory
import com.clipevery.dao.task.TaskStatus
import com.clipevery.task.FailureClipTaskResult
import com.clipevery.task.extra.BaseExtraInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.serializer
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass

object TaskUtils {

    fun createTask(
        clipDataId: ObjectId?,
        taskType: Int,
        extraInfo: ClipTaskExtraInfo = BaseExtraInfo(),
    ): ClipTask {
        return ClipTask().apply {
            this.clipDataId = clipDataId
            this.taskType = taskType
            this.status = TaskStatus.PREPARING
            this.createTime = System.currentTimeMillis()
            this.modifyTime = System.currentTimeMillis()
            this.extraInfo = DesktopJsonUtils.JSON.encodeToString(extraInfo)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ClipTaskExtraInfo> getExtraInfo(
        clipTask: ClipTask,
        kclass: KClass<T>,
    ): T {
        val serializer = Json.serializersModule.serializer(kclass.java)
        return DesktopJsonUtils.JSON.decodeFromString(serializer, clipTask.extraInfo) as T
    }

    fun createFailExtraInfo(
        clipTask: ClipTask,
        throwable: Throwable,
    ): String {
        val currentTime = System.currentTimeMillis()
        val executionHistory =
            ExecutionHistory(
                startTime = clipTask.modifyTime,
                endTime = currentTime,
                status = TaskStatus.FAILURE,
                message = throwable.message ?: "Unknown error",
            )
        val jsonObject = DesktopJsonUtils.JSON.decodeFromString<JsonObject>(clipTask.extraInfo)

        val mutableJsonObject = jsonObject.toMutableMap()

        val jsonElement = DesktopJsonUtils.JSON.encodeToJsonElement(ExecutionHistory.serializer(), executionHistory)
        mutableJsonObject["executionHistories"]?.let {
            val list = it.jsonArray.toMutableList()
            list.add(jsonElement)
            mutableJsonObject["executionHistories"] = JsonArray(list)
        } ?: run {
            mutableJsonObject["executionHistories"] = JsonArray(listOf(jsonElement))
        }
        return DesktopJsonUtils.JSON.encodeToString(JsonObject(mutableJsonObject))
    }

    fun createFailureClipTaskResult(
        retryHandler: () -> Boolean,
        startTime: Long,
        failMessage: String,
        extraInfo: ClipTaskExtraInfo,
    ): FailureClipTaskResult {
        val needRetry = retryHandler()
        extraInfo.executionHistories.add(ExecutionHistory(startTime, System.currentTimeMillis(), TaskStatus.FAILURE, failMessage))
        return FailureClipTaskResult(DesktopJsonUtils.JSON.encodeToString(extraInfo), needRetry)
    }
}
