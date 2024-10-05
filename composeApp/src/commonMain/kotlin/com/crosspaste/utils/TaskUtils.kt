package com.crosspaste.utils

import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.realm.task.ExecutionHistory
import com.crosspaste.realm.task.PasteTask
import com.crosspaste.realm.task.PasteTaskExtraInfo
import com.crosspaste.realm.task.TaskStatus
import com.crosspaste.task.FailurePasteTaskResult
import com.crosspaste.task.extra.BaseExtraInfo
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.datetime.Clock
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.serializer
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass

object TaskUtils {

    private val jsonUtils = getJsonUtils()

    fun createTask(
        pasteDataId: ObjectId?,
        taskType: Int,
        extraInfo: PasteTaskExtraInfo = BaseExtraInfo(),
    ): PasteTask {
        return PasteTask().apply {
            this.pasteDataId = pasteDataId
            this.taskType = taskType
            this.status = TaskStatus.PREPARING
            this.createTime = Clock.System.now().toEpochMilliseconds()
            this.modifyTime = Clock.System.now().toEpochMilliseconds()
            this.extraInfo = jsonUtils.JSON.encodeToString(extraInfo)
        }
    }

    @OptIn(InternalSerializationApi::class)
    fun <T : PasteTaskExtraInfo> getExtraInfo(
        pasteTask: PasteTask,
        kclass: KClass<T>,
    ): T {
        val serializer = kclass.serializer()
        return jsonUtils.JSON.decodeFromString(serializer, pasteTask.extraInfo)
    }

    fun createFailExtraInfo(
        pasteTask: PasteTask,
        throwable: Throwable,
    ): String {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val executionHistory =
            ExecutionHistory(
                startTime = pasteTask.modifyTime,
                endTime = currentTime,
                status = TaskStatus.FAILURE,
                message = throwable.message ?: "Unknown error",
            )
        val jsonObject = jsonUtils.JSON.decodeFromString<JsonObject>(pasteTask.extraInfo)

        val mutableJsonObject = jsonObject.toMutableMap()

        val jsonElement = jsonUtils.JSON.encodeToJsonElement(ExecutionHistory.serializer(), executionHistory)
        mutableJsonObject["executionHistories"]?.let {
            val list = it.jsonArray.toMutableList()
            list.add(jsonElement)
            mutableJsonObject["executionHistories"] = JsonArray(list)
        } ?: run {
            mutableJsonObject["executionHistories"] = JsonArray(listOf(jsonElement))
        }
        return jsonUtils.JSON.encodeToString(JsonObject(mutableJsonObject))
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

        extraInfo.executionHistories.add(
            ExecutionHistory(
                startTime,
                Clock.System.now().toEpochMilliseconds(),
                TaskStatus.FAILURE,
                failMessage,
            ),
        )
        return FailurePasteTaskResult(jsonUtils.JSON.encodeToString(extraInfo), needRetry)
    }

    private fun getStackTraceAsString(throwable: Throwable): String {
        val messages = mutableListOf<String>()
        var currentThrowable: Throwable? = throwable
        while (currentThrowable != null) {
            val message = "${currentThrowable::class.simpleName}: ${currentThrowable.message}"
            messages.add(message)
            currentThrowable = currentThrowable.cause
        }
        return messages.joinToString("\n")
    }
}
