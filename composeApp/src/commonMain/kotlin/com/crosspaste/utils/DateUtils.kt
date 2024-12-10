package com.crosspaste.utils

import io.realm.kotlin.types.RealmInstant
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

fun getDateUtils(): DateUtils {
    return DateUtils
}

object DateUtils {

    val TIME_ZONE: TimeZone = TimeZone.currentSystemDefault()

    @OptIn(FormatStringsInDatetimeFormats::class)
    val YMD_FORMAT = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd") }

    fun getRealmInstantOffsetDay(days: Int): RealmInstant {
        val now = Clock.System.now()
        val offsetDay = now.plus(days.days)
        return RealmInstant.from(
            epochSeconds = offsetDay.epochSeconds,
            nanosecondAdjustment = offsetDay.nanosecondsOfSecond,
        )
    }

    fun getDateDesc(date: LocalDateTime): String? {
        val now = now()

        if (date.date == now.date) {
            val hoursDiff = now.hour - date.hour
            val minutesDiff = now.minute - date.minute
            val secondsDiff = now.second - date.second

            if (hoursDiff < 1 && minutesDiff < 1 && secondsDiff < 60) {
                return "just_now"
            }
            return "today"
        }

        val yesterday =
            Clock.System.now()
                .minus(1.days)
                .toLocalDateTime(TimeZone.currentSystemDefault())

        if (date.date == yesterday.date) {
            return "yesterday"
        }

        return null
    }

    fun getYMD(date: LocalDateTime = now()): String {
        return YMD_FORMAT.format(date)
    }

    @OptIn(FormatStringsInDatetimeFormats::class)
    fun getDateDesc(
        date: LocalDateTime,
        options: DateTimeFormatOptions,
        locale: String,
    ): String {
        val dateTimeFormat =
            LocalDateTime.Format {
                byUnicodePattern(
                    "${options.dateStyle.toPattern(locale)} ${options.timeStyle.toPattern(locale, options.hour12)}",
                )
            }
        return dateTimeFormat.format(date)
    }

    fun convertRealmInstantToLocalDateTime(realmInstant: RealmInstant): LocalDateTime {
        // Get seconds and nanoseconds from RealmInstant
        val epochSeconds = realmInstant.epochSeconds
        val nanosecondsOfSecond = realmInstant.nanosecondsOfSecond

        return Instant.fromEpochSeconds(epochSeconds, nanosecondsOfSecond)
            .toLocalDateTime(TIME_ZONE)
    }

    fun now(): LocalDateTime {
        val currentInstant: Instant = Clock.System.now()
        return currentInstant.toLocalDateTime(TIME_ZONE)
    }
}
