package com.crosspaste.utils

import com.crosspaste.db.task.ExecutionHistory
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.PasteTaskExtraInfo
import com.crosspaste.db.task.TaskStatus
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.task.FailurePasteTaskResult
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object TaskUtils {

    private val jsonUtils = getJsonUtils()

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
        val currentTime = nowEpochMilliseconds()
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
                nowEpochMilliseconds(),
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
