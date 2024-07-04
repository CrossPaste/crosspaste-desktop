package com.crosspaste.utils

import com.crosspaste.dao.task.ExecutionHistory
import com.crosspaste.dao.task.PasteTask
import com.crosspaste.dao.task.PasteTaskExtraInfo
import com.crosspaste.dao.task.TaskStatus
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.task.FailurePasteTaskResult
import com.crosspaste.task.extra.BaseExtraInfo
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.serializer
import org.mongodb.kbson.ObjectId
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KClass

object TaskUtils {

    fun createTask(
        pasteDataId: ObjectId?,
        taskType: Int,
        extraInfo: PasteTaskExtraInfo = BaseExtraInfo(),
    ): PasteTask {
        return PasteTask().apply {
            this.pasteDataId = pasteDataId
            this.taskType = taskType
            this.status = TaskStatus.PREPARING
            this.createTime = System.currentTimeMillis()
            this.modifyTime = System.currentTimeMillis()
            this.extraInfo = DesktopJsonUtils.JSON.encodeToString(extraInfo)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : PasteTaskExtraInfo> getExtraInfo(
        pasteTask: PasteTask,
        kclass: KClass<T>,
    ): T {
        val serializer = Json.serializersModule.serializer(kclass.java)
        return DesktopJsonUtils.JSON.decodeFromString(serializer, pasteTask.extraInfo) as T
    }

    fun createFailExtraInfo(
        pasteTask: PasteTask,
        throwable: Throwable,
    ): String {
        val currentTime = System.currentTimeMillis()
        val executionHistory =
            ExecutionHistory(
                startTime = pasteTask.modifyTime,
                endTime = currentTime,
                status = TaskStatus.FAILURE,
                message = throwable.message ?: "Unknown error",
            )
        val jsonObject = DesktopJsonUtils.JSON.decodeFromString<JsonObject>(pasteTask.extraInfo)

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

    fun createFailurePasteTaskResult(
        logger: KLogger,
        retryHandler: () -> Boolean,
        startTime: Long,
        fails: Collection<FailureResult>,
        extraInfo: PasteTaskExtraInfo,
    ): FailurePasteTaskResult {
        val needRetry = retryHandler()

        val failMessage = fails.joinToString(separator = "\n", limit = 3) { getStackTraceAsString(it.exception) }

        if (!needRetry) {
            logger.error { failMessage }
        }

        extraInfo.executionHistories.add(ExecutionHistory(startTime, System.currentTimeMillis(), TaskStatus.FAILURE, failMessage))
        return FailurePasteTaskResult(DesktopJsonUtils.JSON.encodeToString(extraInfo), needRetry)
    }

    private fun getStackTraceAsString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
}
