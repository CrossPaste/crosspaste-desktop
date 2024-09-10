package com.crosspaste.realm.task

import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import org.mongodb.kbson.ObjectId

class PasteTaskRealm(private val realm: Realm) {

    suspend fun createTask(pasteTask: PasteTask): ObjectId {
        realm.write {
            copyToRealm(pasteTask)
        }
        return pasteTask.taskId
    }

    suspend fun update(
        taskId: ObjectId,
        copeFromRealm: Boolean = false,
        block: PasteTask.() -> Unit,
    ): PasteTask? {
        return realm.write {
            query(PasteTask::class, "taskId = $0", taskId).first().find()?.let {
                it.apply(block)
                return@write if (copeFromRealm) copyFromRealm(it) else null
            }
        }
    }

    fun getTask(pasteDataId: ObjectId): RealmResults<PasteTask> {
        return realm.query(
            PasteTask::class,
            "pasteDataId = $0 AND taskType = $1",
            pasteDataId,
            TaskType.PULL_FILE_TASK,
        )
            .find()
    }
}
