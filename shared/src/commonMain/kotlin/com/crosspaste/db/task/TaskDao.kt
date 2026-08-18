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

    /** The highest persisted task id, or 0 when no tasks exist. */
    suspend fun getMaxTaskId(): Long

    /**
     * Atomically claims tasks a previous process left unfinished so they can be
     * re-enqueued after startup: returns up to [limit] ids of PREPARING and
     * EXECUTING tasks with taskId <= [maxTaskId], resetting the claimed
     * EXECUTING ones back to PREPARING in the same transaction. Rows beyond
     * [limit] keep their status and are claimed on a later startup.
     *
     * Stale policy: the single-instance app lock guarantees that at startup
     * every unfinished row at or below [maxTaskId] belongs to a dead process,
     * so all of them are recoverable. [maxTaskId] must be captured (via
     * [getMaxTaskId]) before any task producer of the current process starts:
     * task ids are monotonic (AUTOINCREMENT), so producer tasks always exceed
     * the bound and can never be claimed — recovery cannot enqueue them twice,
     * regardless of wall-clock changes.
     */
    suspend fun claimRecoverableTasks(
        maxTaskId: Long,
        limit: Long = 1000,
    ): List<Long>

    suspend fun cleanSuccessTask(time: Long)

    suspend fun cleanFailureTask(time: Long)
}
