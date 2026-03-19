package com.crosspaste.utils

data class RelativeTime(
    val value: Int? = null,
    val unit: String,
)

@Suppress("Unused")
object RelativeTimeKey {
    const val NOW = "relative_now"
    const val SECONDS = "relative_seconds_ago"
    const val MINUTES = "relative_minutes_ago"
    const val HOURS = "relative_hours_ago"
    const val DAYS = "relative_days_ago"
    const val WEEKS = "relative_weeks_ago"
    const val MONTHS = "relative_months_ago"
    const val YEARS = "relative_years_ago"
}
