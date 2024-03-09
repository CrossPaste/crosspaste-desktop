package com.clipevery.dao.task

import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ClipTask: RealmObject {
    @PrimaryKey
    var taskId: ObjectId = ObjectId()

    @Index
    var clipId: Int = 0

    @Index
    var taskType: Int = TaskType.UNKNOWN_TASK

    @Index
    var status: Int = 0

    @Index
    var createTime: RealmInstant = RealmInstant.now()

    @Index
    var modifyTime: RealmInstant = RealmInstant.now()

    var extraInfo: String = ""

}