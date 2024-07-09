package com.crosspaste.utils

import io.realm.kotlin.types.RealmInstant
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

expect fun getDateUtils(): DateUtils

interface DateUtils {

    fun getPrevDay(): RealmInstant

    fun getRealmInstant(days: Int): RealmInstant

    fun getDateText(date: LocalDateTime): String?

    fun getYYYYMMDD(date: LocalDateTime = getCurrentLocalDateTime()): String

    fun getDateText(
        date: LocalDateTime,
        pattern: String,
        locale: String,
    ): String

    fun convertRealmInstantToLocalDateTime(realmInstant: RealmInstant): LocalDateTime
}

fun getCurrentLocalDateTime(): LocalDateTime {
    val currentInstant: Instant = Clock.System.now()
    return currentInstant.toLocalDateTime(TimeZone.currentSystemDefault())
}
