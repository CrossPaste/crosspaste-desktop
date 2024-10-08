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

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun kotlinx.datetime.LocalDateTime.toJavaLocalDateTime(): LocalDateTime {
        return LocalDateTime.of(
            this.year,
            this.monthNumber,
            this.dayOfMonth,
            this.hour,
            this.minute,
            this.second,
            this.nanosecond,
        )
    }

    private fun LocalDateTime.toKotlinLocalDateTime(): kotlinx.datetime.LocalDateTime {
        return kotlinx.datetime.LocalDateTime(
            this.year,
            this.monthValue,
            this.dayOfMonth,
            this.hour,
            this.minute,
            this.second,
            this.nano,
        )
    }

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

    override fun getDateText(date: kotlinx.datetime.LocalDateTime): String? {
        val now = LocalDateTime.now()

        val javaDate = date.toJavaLocalDateTime()

        if (javaDate.toLocalDate().isEqual(now.toLocalDate())) {
            val hour = ChronoUnit.HOURS.between(javaDate, now)
            val minutes = ChronoUnit.MINUTES.between(javaDate, now)
            val seconds = ChronoUnit.SECONDS.between(javaDate, now)

            if (hour < 1 && minutes < 1 && seconds < 60) {
                return "just_now"
            }
            return "today"
        }

        val yesterday = now.minusDays(1)
        if (javaDate.toLocalDate().isEqual(yesterday.toLocalDate())) {
            return "yesterday"
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
        date: kotlinx.datetime.LocalDateTime,
        pattern: String,
        locale: String,
    ): String {
        return memoizeFormat(Pair(pattern, stringToLocale(locale)))(date.toJavaLocalDateTime())
    }

    override fun getYYYYMMDD(date: kotlinx.datetime.LocalDateTime): String {
        return dateFormatter.format(date.toJavaLocalDateTime())
    }

    override fun convertRealmInstantToLocalDateTime(realmInstant: RealmInstant): kotlinx.datetime.LocalDateTime {
        // Get seconds and nanoseconds from RealmInstant
        val epochSeconds = realmInstant.epochSeconds
        val nanosecondsOfSecond = realmInstant.nanosecondsOfSecond

        // Create an Instant using Instant.ofEpochSecond
        val instant = Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

        // Convert Instant to LocalDateTime using the system default time zone
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).toKotlinLocalDateTime()
    }

    fun stringToLocale(localeString: String): Locale {
        val parts = localeString.split('_')
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            3 -> Locale(parts[0], parts[1], parts[2])
            else -> throw IllegalArgumentException("Invalid locale string: $localeString")
        }
    }
}
