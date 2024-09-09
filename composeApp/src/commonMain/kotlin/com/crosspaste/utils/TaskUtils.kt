package com.crosspaste.utils

import com.crosspaste.dao.task.PasteTask
import com.crosspaste.dao.task.PasteTaskExtraInfo
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.task.FailurePasteTaskResult
import com.crosspaste.task.extra.BaseExtraInfo
import io.github.oshai.kotlinlogging.KLogger
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass

expect fun getTaskUtils(): TaskUtils

interface TaskUtils {

    fun createTask(
        pasteDataId: ObjectId?,
        taskType: Int,
        extraInfo: PasteTaskExtraInfo = BaseExtraInfo(),
    ): PasteTask

    fun <T : PasteTaskExtraInfo> getExtraInfo(
        pasteTask: PasteTask,
        kclass: KClass<T>,
    ): T

    fun createFailExtraInfo(
        pasteTask: PasteTask,
        throwable: Throwable,
    ): String

    fun createFailurePasteTaskResult(
        logger: KLogger,
        retryHandler: () -> Boolean,
        startTime: Long,
        fails: Collection<FailureResult>,
        extraInfo: PasteTaskExtraInfo,
    ): FailurePasteTaskResult
}
