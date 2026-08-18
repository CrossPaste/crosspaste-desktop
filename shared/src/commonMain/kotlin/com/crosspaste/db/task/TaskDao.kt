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

    /**
     * Atomically claims tasks a previous process left unfinished so they can be
     * re-enqueued after startup: returns the ids of PREPARING and EXECUTING
     * tasks created strictly before [createdBefore], resetting the EXECUTING
     * ones back to PREPARING in the same transaction.
     *
     * Stale policy: the single-instance app lock guarantees that at startup
     * every unfinished row older than [createdBefore] belongs to a dead
     * process, so all of them are recoverable. [createdBefore] must be
     * captured before any task producer of the current process starts: tasks
     * that producers create afterwards then have createTime >= the bound and
     * can never be claimed, so recovery cannot enqueue them twice.
     */
    suspend fun claimRecoverableTasks(createdBefore: Long): List<Long>

    suspend fun cleanSuccessTask(time: Long)

    suspend fun cleanFailureTask(time: Long)
}
