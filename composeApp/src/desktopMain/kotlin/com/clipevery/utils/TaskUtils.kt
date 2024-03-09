package com.clipevery.utils

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.dao.task.TaskStatus
import com.clipevery.task.extra.BaseExtraInfo
import io.realm.kotlin.types.RealmInstant
import kotlinx.serialization.encodeToString

object TaskUtils {

    fun createTask(clipId: Int, taskType: Int, extraInfo: ClipTaskExtraInfo = BaseExtraInfo()): ClipTask {
        return ClipTask().apply {
            this.clipId = clipId
            this.taskType = taskType
            this.status = TaskStatus.PREPARING
            this.createTime = RealmInstant.now()
            this.modifyTime = RealmInstant.now()
            this.extraInfo = JsonUtils.JSON.encodeToString(extraInfo)
        }
    }

    inline fun <reified T: ClipTaskExtraInfo> getExtraInfo(clipTask: ClipTask): T {
        return JsonUtils.JSON.decodeFromString(clipTask.extraInfo)
    }
}