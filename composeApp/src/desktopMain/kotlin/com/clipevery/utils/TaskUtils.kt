package com.clipevery.utils

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.dao.task.TaskStatus
import com.clipevery.task.extra.BaseExtraInfo
import io.realm.kotlin.types.RealmInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object TaskUtils {

    fun createTask(clipId: Int, taskType: Int, extraInfo: ClipTaskExtraInfo = BaseExtraInfo()): ClipTask {
        return ClipTask().apply {
            this.clipId = clipId
            this.taskType = taskType
            this.status = TaskStatus.PREPARING
            this.createTime = System.currentTimeMillis()
            this.modifyTime = System.currentTimeMillis()
            this.extraInfo = JsonUtils.JSON.encodeToString(extraInfo)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: ClipTaskExtraInfo> getExtraInfo(clipTask: ClipTask, kclass: KClass<T>): T {
        val serializer = Json.serializersModule.serializer(kclass.java)
        return JsonUtils.JSON.decodeFromString(serializer, clipTask.extraInfo) as T
    }
}