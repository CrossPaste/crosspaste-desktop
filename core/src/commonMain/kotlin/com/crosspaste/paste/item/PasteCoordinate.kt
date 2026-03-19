package com.crosspaste.paste.item

import com.crosspaste.utils.DateUtils

open class PasteCoordinate(
    open val id: Long,
    open val appInstanceId: String,
    open val createTime: Long = DateUtils.nowEpochMilliseconds(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteCoordinate) return false

        if (id != other.id) return false
        if (appInstanceId != other.appInstanceId) return false
        if (createTime != other.createTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        result = 31 * result + createTime.hashCode()
        return result
    }
}
