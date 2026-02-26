package com.crosspaste.db.task

interface TaskDao {

    fun createTaskBlock(
        pasteDataId: Long?,
        taskType: Int,
        extraInfo: PasteTaskExtraInfo = BaseExtraInfo(),
    ): Long

    suspend fun createTask(
        pasteDataId: Long?,
        taskType: Int,
        extraInfo: PasteTaskExtraInfo = BaseExtraInfo(),
    ): Long

    suspend fun executingTask(taskId: Long)

    suspend fun successTask(
        taskId: Long,
        newExtraInfo: String?,
    )

    suspend fun failureTask(
        taskId: Long,
        needRetry: Boolean,
        newExtraInfo: String?,
    )

    suspend fun getTask(taskId: Long): PasteTask?

    suspend fun cleanSuccessTask(time: Long)

    suspend fun cleanFailureTask(time: Long)
}
