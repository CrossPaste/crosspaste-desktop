package com.crosspaste.task

import com.crosspaste.app.AppControl
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import com.crosspaste.paste.PasteType

class DesktopTaskSubmitter(
    private val appControl: AppControl,
    private val configManager: DesktopConfigManager,
    private val taskDao: TaskDao,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
) : TaskSubmitter {

    private val taskExecutor by lazy { lazyTaskExecutor.value }

    override suspend fun submit(block: suspend TaskBuilder.() -> Unit) {
        val builder = DesktopTaskBuilder(appControl, configManager, taskDao)
        builder.block()
        val tasks = builder.getTasks()
        if (tasks.isNotEmpty()) {
            taskExecutor.submitTasks(tasks)
        }
    }
}

class DesktopTaskBuilder(
    private val appControl: AppControl,
    private val configManager: DesktopConfigManager,
    private val taskDao: TaskDao,
) : TaskBuilder {

    private val taskIds = mutableListOf<Long>()

    override fun addDeletePasteTasks(ids: List<Long>): TaskBuilder {
        ids.forEach { id ->
            taskIds.add(taskDao.createTaskBlock(id, TaskType.DELETE_PASTE_TASK))
        }
        return this
    }

    override fun addPullFileTask(
        id: Long,
        remotePasteDataId: Long,
    ): TaskBuilder {
        taskIds.add(
            taskDao.createTaskBlock(
                id,
                TaskType.PULL_FILE_TASK,
                PullExtraInfo(remotePasteDataId),
            ),
        )
        return this
    }

    override fun addSyncTask(
        id: Long,
        appInstanceId: String,
        fileSize: Long,
    ): TaskBuilder {
        if (appControl.isFileSizeSyncEnabled(fileSize)) {
            taskIds.add(
                taskDao.createTaskBlock(
                    id,
                    TaskType.SYNC_PASTE_TASK,
                    SyncExtraInfo(appInstanceId),
                ),
            )
        }
        return this
    }

    override fun addRelaySyncTask(
        id: Long,
        appInstanceId: String,
    ): TaskBuilder {
        if (configManager.getCurrentConfig().enableClipboardRelay) {
            taskIds.add(
                taskDao.createTaskBlock(
                    id,
                    TaskType.SYNC_PASTE_TASK,
                    SyncExtraInfo(appInstanceId),
                ),
            )
            return this
        } else {
            return this
        }
    }

    override fun addPullIconTask(
        id: Long,
        existIconFile: Boolean,
    ): TaskBuilder {
        if (!existIconFile) {
            taskIds.add(taskDao.createTaskBlock(id, TaskType.PULL_ICON_TASK))
        }
        return this
    }

    override fun addRenderingTask(
        id: Long,
        pasteType: PasteType,
    ): TaskBuilder {
        if (pasteType.isUrl()) {
            taskIds.add(taskDao.createTaskBlock(id, TaskType.OPEN_GRAPH_TASK))
        }
        return this
    }

    fun getTasks(): List<Long> = taskIds
}
