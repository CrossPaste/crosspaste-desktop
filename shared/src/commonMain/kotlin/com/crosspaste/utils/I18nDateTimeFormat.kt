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
                    "zh", "zh_hant" -> "yyyy年MM月dd日"
                    "ja" -> "yyyy年MM月dd日"
                    "ko" -> "yyyy년 MM월 dd일"
                    "es" -> "dd/MM/yyyy"
                    "de" -> "dd.MM.yyyy"
                    "fr" -> "dd/MM/yyyy"
                    "fa" -> "yyyy/MM/dd"
                    else -> "MM/dd/yyyy"
                }
            LONG ->
                when (locale) {
                    "zh", "zh_hant" -> "yyyy年MM月dd日"
                    "ja" -> "yyyy年MM月dd日"
                    "ko" -> "yyyy년 MM월 dd일"
                    "es" -> "dd/MM/yyyy"
                    "de" -> "dd.MM.yyyy"
                    "fr" -> "dd/MM/yyyy"
                    "fa" -> "yyyy/MM/dd"
                    else -> "MM/dd/yyyy"
                }
            MEDIUM ->
                when (locale) {
                    "zh", "zh_hant" -> "yyyy年MM月dd日"
                    "ja" -> "yyyy年MM月dd日"
                    "ko" -> "yyyy. MM. dd"
                    "es", "fr" -> "dd/MM/yyyy"
                    "de" -> "dd.MM.yyyy"
                    "fa" -> "yyyy/MM/dd"
                    else -> "MM/dd/yyyy"
                }
            SHORT ->
                when (locale) {
                    "zh", "zh_hant", "ja" -> "yyyy/MM/dd"
                    "ko" -> "yy. M. d"
                    "es" -> "d/M/yy"
                    "de" -> "dd.MM.yy"
                    "fr" -> "dd/MM/yy"
                    "fa" -> "yy/M/d"
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
            FULL -> "HH:mm:ss zzzz"
            LONG ->
                when (locale) {
                    "ko" -> "HH시 mm분 ss초 z"
                    else -> "HH:mm:ss z"
                }
            MEDIUM ->
                when (locale) {
                    "ja" -> "HH時mm分ss秒"
                    else -> "HH:mm:ss"
                }
            SHORT ->
                when (locale) {
                    "ja" -> "HH時mm分"
                    else -> "HH:mm"
                }
        }
}
