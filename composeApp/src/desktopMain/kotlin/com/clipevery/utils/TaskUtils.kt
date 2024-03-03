package com.clipevery.utils

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskStatus
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant

object TaskUtils {

    fun createTask(clipId: Int, taskType: Int, extraInfo: RealmAny? = null): ClipTask {
        return ClipTask().apply {
            this.clipId = clipId
            this.taskType = taskType
            this.status = TaskStatus.PREPARING
            this.createTime = RealmInstant.now()
            this.modifyTime = RealmInstant.now()
            this.extraInfo = extraInfo
        }
    }
}