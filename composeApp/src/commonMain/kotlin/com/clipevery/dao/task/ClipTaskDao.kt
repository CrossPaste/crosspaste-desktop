package com.clipevery.dao.task

import org.mongodb.kbson.ObjectId

interface ClipTaskDao {

    suspend fun update(taskId: ObjectId, block: ClipTask.() -> Unit)

    suspend fun executingAndGet(taskId: ObjectId): ClipTask?

    suspend fun success(taskId: ObjectId)

    suspend fun failAndGet(taskId: ObjectId, e: Throwable): ClipTask?

    suspend fun unexpectFail(taskId: ObjectId, e: Throwable)

    suspend fun reset(taskId: ObjectId)
}