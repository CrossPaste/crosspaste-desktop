package com.crosspaste.utils

import io.realm.kotlin.types.RealmInstant
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

actual fun getDateUtils(): DateUtils {
    return DesktopDateUtils
}

object DesktopDateUtils : DateUtils {

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun getPrevDay(): RealmInstant {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        // Convert milliseconds to seconds for the epochSeconds parameter
        val epochSeconds = calendar.timeInMillis / 1000
        // The nanosecondAdjustment parameter is 0 as we're not adjusting nanoseconds here
        return RealmInstant.from(epochSeconds, 0)
    }

    override fun getRealmInstant(days: Int): RealmInstant {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, days)
        // Convert milliseconds to seconds for the epochSeconds parameter
        val epochSeconds = calendar.timeInMillis / 1000
        // The nanosecondAdjustment parameter is 0 as we're not adjusting nanoseconds here
        return RealmInstant.from(epochSeconds, 0)
    }

    override fun getDateText(date: LocalDateTime): String? {
        val now = LocalDateTime.now()

        if (date.toLocalDate().isEqual(now.toLocalDate())) {
            val hour = ChronoUnit.HOURS.between(date, now)
            val minutes = ChronoUnit.MINUTES.between(date, now)
            val seconds = ChronoUnit.SECONDS.between(date, now)

            if (hour < 1 && minutes < 1 && seconds < 60) {
                return "Just_now"
            }
            return "Today"
        }

        val yesterday = now.minusDays(1)
        if (date.toLocalDate().isEqual(yesterday.toLocalDate())) {
            return "Yesterday"
        }

        return null
    }

    private val formatter: (Pair<String, Locale>) -> (LocalDateTime) -> String = { pair ->
        { date: LocalDateTime ->
            DateTimeFormatter.ofPattern(pair.first, pair.second).format(date)
        }
    }

    private val memoizeFormat = Memoize.memoize(formatter)

    override fun getDateText(
        date: LocalDateTime,
        pattern: String,
        locale: Locale,
    ): String {
        return memoizeFormat(Pair(pattern, locale))(date)
    }

    override fun getYYYYMMDD(date: LocalDateTime): String {
        return dateFormatter.format(date)
    }

    override fun convertRealmInstantToLocalDateTime(realmInstant: RealmInstant): LocalDateTime {
        // 1. 从 RealmInstant 获取秒和纳秒
        val epochSeconds = realmInstant.epochSeconds
        val nanosecondsOfSecond = realmInstant.nanosecondsOfSecond

        // 2. 使用 Instant.ofEpochSecond 创建 Instant
        val instant = Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

        // 3. 使用系统默认的时区将 Instant 转换为 LocalDateTime
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    }
}
