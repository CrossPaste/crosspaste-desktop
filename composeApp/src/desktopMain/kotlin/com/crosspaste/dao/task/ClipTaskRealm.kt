package com.crosspaste.dao.task

import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import org.mongodb.kbson.ObjectId

class ClipTaskRealm(private val realm: Realm) : ClipTaskDao {

    override suspend fun createTask(clipTask: ClipTask): ObjectId {
        realm.write {
            copyToRealm(clipTask)
        }
        return clipTask.taskId
    }

    override suspend fun update(
        taskId: ObjectId,
        copeFromRealm: Boolean,
        block: ClipTask.() -> Unit,
    ): ClipTask? {
        return realm.write {
            query(ClipTask::class, "taskId = $0", taskId).first().find()?.let {
                it.apply(block)
                return@write if (copeFromRealm) copyFromRealm(it) else null
            }
        }
    }

    override fun getTask(clipDataId: ObjectId): RealmResults<ClipTask> {
        return realm.query(
            ClipTask::class,
            "clipDataId = $0 AND taskType = $1",
            clipDataId,
            TaskType.PULL_FILE_TASK,
        )
            .find()
    }
}
