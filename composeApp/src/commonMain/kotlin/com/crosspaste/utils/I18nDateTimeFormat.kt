package com.crosspaste.utils

data class DateTimeFormatOptions(
    val dateStyle: DateStyle = DateStyle.MEDIUM,
    val timeStyle: TimeStyle = TimeStyle.MEDIUM,
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
                    "en" -> "EEEE, MM/dd/yyyy"
                    "es" -> "EEEE, dd 'de' MM 'de' yyyy"
                    else -> "EEEE, MM/dd/yyyy"
                }
            LONG ->
                when (locale) {
                    "zh" -> "yyyy年MM月dd日"
                    "ja" -> "yyyy年MM月dd日"
                    "en" -> "MM/dd/yyyy"
                    "es" -> "dd 'de' MM 'de' yyyy"
                    else -> "MM/dd/yyyy"
                }
            MEDIUM ->
                when (locale) {
                    "zh" -> "yyyy年MM月dd日"
                    "ja" -> "yyyy年MM月dd日"
                    "en" -> "MM/dd/yyyy"
                    "es" -> "dd/MM/yyyy"
                    else -> "MM/dd/yyyy"
                }
            SHORT ->
                when (locale) {
                    "zh" -> "yyyy/MM/dd"
                    "ja" -> "yyyy/MM/dd"
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

    internal fun toPattern(locale: String): String =
        when (this) {
            FULL ->
                when (locale) {
                    "zh", "ja" -> "HH:mm:ss"
                    else -> "HH:mm:ss z"
                }
            LONG ->
                when (locale) {
                    "zh", "ja" -> "HH:mm:ss"
                    else -> "HH:mm:ss"
                }
            MEDIUM ->
                when (locale) {
                    "zh" -> "HH:mm:ss"
                    "ja" -> "HH時mm分ss秒"
                    else -> "HH:mm:ss"
                }
            SHORT ->
                when (locale) {
                    "zh" -> "HH:mm"
                    "ja" -> "HH時mm分"
                    else -> "HH:mm"
                }
        }
}
