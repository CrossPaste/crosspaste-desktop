package com.crosspaste.utils

import com.crosspaste.ui.base.RelativeTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun getDateUtils(): DateUtils = DateUtils

@OptIn(ExperimentalTime::class)
object DateUtils {

    val TIME_ZONE: TimeZone = TimeZone.currentSystemDefault()

    @OptIn(FormatStringsInDatetimeFormats::class)
    val YMD_FORMAT = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd") }

    fun getOffsetDay(
        currentTime: Instant = nowInstant(),
        days: Int,
    ): Long {
        val offsetDay = currentTime.plus(days.days)
        return offsetDay.toEpochMilliseconds()
    }

    fun getYMD(date: LocalDateTime = now()): String = YMD_FORMAT.format(date)

    @OptIn(FormatStringsInDatetimeFormats::class)
    fun getDateDesc(
        date: LocalDateTime,
        options: DateTimeFormatOptions,
        locale: String,
    ): String {
        val dateTimeFormat =
            LocalDateTime.Format {
                byUnicodePattern(
                    "${options.dateStyle.toPattern(locale)} ${options.timeStyle.toPattern(locale)}",
                )
            }
        return dateTimeFormat.format(date)
    }

    fun now(): LocalDateTime {
        val currentInstant: Instant = nowInstant()
        return currentInstant.toLocalDateTime(TIME_ZONE)
    }

    fun nowEpochMilliseconds(): Long = nowInstant().toEpochMilliseconds()

    fun nowInstant(): Instant = Clock.System.now()

    fun epochMillisecondsToLocalDateTime(epochMilliseconds: Long): LocalDateTime {
        val instant = Instant.fromEpochMilliseconds(epochMilliseconds)
        return instant.toLocalDateTime(TIME_ZONE)
    }

    fun getRelativeTime(
        timestamp: Long,
        now: Long = nowEpochMilliseconds(),
    ): RelativeTime {
        val duration = (now - timestamp).milliseconds

        return when {
            duration < 10.seconds -> RelativeTime(null, "relative_now")

            duration < 1.minutes -> {
                val seconds = duration.inWholeSeconds.toInt()
                RelativeTime(seconds, "relative_seconds_ago")
            }

            duration < 1.hours -> {
                val minutes = duration.inWholeMinutes.toInt()
                RelativeTime(minutes, "relative_minutes_ago")
            }

            duration < 1.days -> {
                val hours = duration.inWholeHours.toInt()
                RelativeTime(hours, "relative_hours_ago")
            }

            duration < 7.days -> {
                val days = duration.inWholeDays.toInt()
                RelativeTime(days, "relative_days_ago")
            }

            duration < 31.days -> {
                val weeks = (duration.inWholeDays / 7).toInt()
                RelativeTime(weeks, "relative_weeks_ago")
            }

            duration < 365.days -> {
                val months = (duration.inWholeDays / 30).toInt()
                RelativeTime(months, "relative_months_ago")
            }

            else -> {
                val years = (duration.inWholeDays / 365).toInt()
                RelativeTime(years, "relative_years_ago")
            }
        }
    }
}
