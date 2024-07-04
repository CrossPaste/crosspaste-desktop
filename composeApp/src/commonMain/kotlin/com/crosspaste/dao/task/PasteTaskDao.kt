package com.crosspaste.dao.task

import io.realm.kotlin.query.RealmResults
import org.mongodb.kbson.ObjectId

interface PasteTaskDao {

    suspend fun createTask(pasteTask: PasteTask): ObjectId

    suspend fun update(
        taskId: ObjectId,
        copeFromRealm: Boolean = false,
        block: PasteTask.() -> Unit,
    ): PasteTask?

    fun getTask(pasteDataId: ObjectId): RealmResults<PasteTask>
}
