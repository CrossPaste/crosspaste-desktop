package com.clipevery.dao.task

import io.realm.kotlin.Realm
import org.mongodb.kbson.ObjectId

class ClipTaskRealm(private val realm: Realm): ClipTaskDao {

    override suspend fun update(taskId: ObjectId, block: ClipTask.() -> Unit) {
        realm.write {
            query(ClipTask::class, "taskId = $0", taskId).first().find()?.let {
                return@write it.apply(block)
            }
        }
    }

    override suspend fun executingAndGet(taskId: ObjectId): ClipTask? {
        return realm.write {
            query(ClipTask::class, "taskId = $0", taskId).first().find()?.let {
                it.status = TaskStatus.EXECUTING
                it.modifyTime = System.currentTimeMillis()
                copyFromRealm(it)
            }
        }
    }

}