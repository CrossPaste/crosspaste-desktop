package com.clipevery.task

import org.mongodb.kbson.ObjectId

interface TaskExecutor {

    suspend fun submitTask(taskId: ObjectId)

    suspend fun submitTasks(taskIds: List<ObjectId>)
}
