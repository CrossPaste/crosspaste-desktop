package com.clipevery.dao.task

import org.mongodb.kbson.ObjectId

interface ClipTaskDao {

    suspend fun update(taskId: ObjectId, block: ClipTask.() -> Unit)

    suspend fun executingAndGet(taskId: ObjectId): ClipTask?

}