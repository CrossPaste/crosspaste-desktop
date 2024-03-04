package com.clipevery.task

import com.clipevery.dao.task.ClipTask

interface SingleTypeTaskExecutor {
    fun executeTask(clipTask: ClipTask)
    fun needRetry(clipTask: ClipTask): Boolean
}