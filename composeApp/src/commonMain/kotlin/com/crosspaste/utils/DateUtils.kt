package com.crosspaste.utils

import io.realm.kotlin.types.RealmInstant
import java.time.LocalDateTime
import java.util.Locale

expect fun getDateUtils(): DateUtils

interface DateUtils {

    fun getPrevDay(): RealmInstant

    fun getRealmInstant(days: Int): RealmInstant

    fun getDateText(date: LocalDateTime): String?

    fun getYYYYMMDD(date: LocalDateTime = LocalDateTime.now()): String

    fun getDateText(
        date: LocalDateTime,
        pattern: String,
        locale: Locale,
    ): String

    fun convertRealmInstantToLocalDateTime(realmInstant: RealmInstant): LocalDateTime
}
