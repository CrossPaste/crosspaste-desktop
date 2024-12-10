package com.crosspaste.utils

data class DateTimeFormatOptions(
    val dateStyle: DateStyle = DateStyle.MEDIUM,
    val timeStyle: TimeStyle = TimeStyle.MEDIUM,
    val hour12: Boolean? = null,
)

enum class DateStyle {
    FULL,
    LONG,
    MEDIUM,
    SHORT,
    ;

    internal fun toPattern(locale: String): String =
        when (this) {
            FULL ->
                when (locale) {
                    "zh" -> "yyyy年MM月dd日 EEEE"
                    "ja" -> "yyyy年MM月dd日 EEEE"
                    "en" -> "EEEE, MMMM d, yyyy"
                    "es" -> "EEEE, d 'de' MMMM 'de' yyyy"
                    else -> "EEEE, MMMM d, yyyy"
                }
            LONG ->
                when (locale) {
                    "zh" -> "yyyy年MM月dd日"
                    "ja" -> "yyyy年MM月dd日"
                    "en" -> "MMMM d, yyyy"
                    "es" -> "d 'de' MMMM 'de' yyyy"
                    else -> "MMMM d, yyyy"
                }
            MEDIUM ->
                when (locale) {
                    "zh" -> "yyyy年MM月dd日"
                    "jp" -> "yyyy年MM月dd日"
                    "en" -> "MMM d, yyyy"
                    "es" -> "d MMM yyyy"
                    else -> "MMM d, yyyy"
                }
            SHORT ->
                when (locale) {
                    "zh" -> "yyyy/MM/dd"
                    "jp" -> "yyyy/MM/dd"
                    "en" -> "M/d/yy"
                    "es" -> "d/M/yy"
                    else -> "M/d/yy"
                }
        }
}

enum class TimeStyle {
    FULL,
    LONG,
    MEDIUM,
    SHORT,
    ;

    internal fun toPattern(
        locale: String,
        hour12: Boolean?,
    ): String {
        val useHour12 =
            hour12 ?: when (locale) {
                "en" -> true
                else -> false
            }

        return when (this) {
            FULL ->
                if (useHour12) {
                    "hh:mm:ss a zzzz"
                } else {
                    "HH:mm:ss zzzz"
                }
            LONG ->
                if (useHour12) {
                    "hh:mm:ss a z"
                } else {
                    "HH:mm:ss z"
                }
            MEDIUM ->
                if (useHour12) {
                    when (locale) {
                        "zh" -> "ah:mm:ss"
                        "jp" -> "ah:mm:ss"
                        else -> "hh:mm:ss a"
                    }
                } else {
                    when (locale) {
                        "zh" -> "HH:mm:ss"
                        "jp" -> "HH時mm分ss秒"
                        else -> "HH:mm:ss"
                    }
                }
            SHORT ->
                if (useHour12) {
                    when (locale) {
                        "zh" -> "ah:mm"
                        "ja" -> "ah:mm"
                        else -> "hh:mm a"
                    }
                } else {
                    when (locale) {
                        "zh" -> "HH:mm"
                        "ja" -> "HH時mm分"
                        else -> "HH:mm"
                    }
                }
        }
    }
}
