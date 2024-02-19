package com.clipevery.utils

import io.realm.kotlin.types.RealmInstant
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

object DateUtils {

    fun getPrevDay(): RealmInstant {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        // Convert milliseconds to seconds for the epochSeconds parameter
        val epochSeconds = calendar.timeInMillis / 1000
        // The nanosecondAdjustment parameter is 0 as we're not adjusting nanoseconds here
        return RealmInstant.from(epochSeconds, 0)
    }

    fun getDateText(date: LocalDateTime): String? {
        val now = LocalDateTime.now()

        if (date.toLocalDate().isEqual(now.toLocalDate())) {
            val hour = ChronoUnit.HOURS.between(date, now)
            val minutes = ChronoUnit.MINUTES.between(date, now)
            val seconds = ChronoUnit.SECONDS.between(date, now)

            if (hour < 1 && minutes < 1 && seconds < 60) {
                return "Just now"
            }
            return "Today"
        }

        val yesterday = now.minusDays(1)
        if (date.toLocalDate().isEqual(yesterday.toLocalDate())) {
            return "Yesterday"
        }

        return null
    }

    fun convertRealmInstantToLocalDateTime(realmInstant: RealmInstant): LocalDateTime {
        // 1. 从 RealmInstant 获取秒和纳秒
        val epochSeconds = realmInstant.epochSeconds
        val nanosecondsOfSecond = realmInstant.nanosecondsOfSecond

        // 2. 使用 Instant.ofEpochSecond 创建 Instant
        val instant = Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

        // 3. 使用系统默认的时区将 Instant 转换为 LocalDateTime
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    }
}