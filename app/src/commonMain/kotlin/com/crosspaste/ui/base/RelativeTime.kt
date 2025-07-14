package com.crosspaste.ui.base

import com.crosspaste.ui.base.RelativeTimeKey.HOURS
import com.crosspaste.ui.base.RelativeTimeKey.MINUTES
import com.crosspaste.ui.base.RelativeTimeKey.NOW
import com.crosspaste.ui.base.RelativeTimeKey.SECONDS

data class RelativeTime(
    val value: Int? = null,
    val unit: String,
) {
    fun getUpdateDelay(): Long? =
        when (unit) {
            NOW -> 10_000L // 10s instead of 1s
            SECONDS -> 30_000L // 30s instead of 5s
            MINUTES -> 60_000L // Keep 1min
            else -> null // No updates
        }

    fun withInHourUnit(): Boolean = unit == HOURS || unit == MINUTES || unit == SECONDS || unit == NOW
}

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
