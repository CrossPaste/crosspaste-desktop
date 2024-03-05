package com.clipevery.dao.task

import io.realm.kotlin.types.RealmObject

interface ClipTaskExtraInfo {

    fun retryCount(): Int

    fun failMessage(): String?

    fun incrementRetryCount()

    fun setFailMessage(message: String)
}

class BaseClipTaskExtraInfo: RealmObject, ClipTaskExtraInfo {

    var retryCount: Int = 0

    var failMessage: String? = null

    override fun retryCount(): Int {
        return retryCount
    }

    override fun failMessage(): String? {
        return failMessage
    }

    override fun incrementRetryCount() {
        retryCount++
    }

    override fun setFailMessage(message: String) {
        failMessage = message
    }

}