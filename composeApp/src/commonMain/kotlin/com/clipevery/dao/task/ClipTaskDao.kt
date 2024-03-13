package com.clipevery.dao.task

import org.mongodb.kbson.ObjectId

interface ClipTaskDao {

    suspend fun update(taskId: ObjectId, copeFromRealm: Boolean = false, block: ClipTask.() -> Unit): ClipTask?
}