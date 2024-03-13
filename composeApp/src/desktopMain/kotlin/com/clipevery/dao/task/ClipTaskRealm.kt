package com.clipevery.dao.task

import io.realm.kotlin.Realm
import org.mongodb.kbson.ObjectId

class ClipTaskRealm(private val realm: Realm): ClipTaskDao {

    override suspend fun update(taskId: ObjectId, copeFromRealm: Boolean, block: ClipTask.() -> Unit): ClipTask? {
        return realm.write {
            query(ClipTask::class, "taskId = $0", taskId).first().find()?.let {
                it.apply(block)
                return@write if (copeFromRealm) copyFromRealm(it) else null
            }
        }
    }
}