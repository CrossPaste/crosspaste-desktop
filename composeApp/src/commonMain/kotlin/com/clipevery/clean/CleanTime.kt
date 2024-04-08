package com.clipevery.clean

enum class CleanTime(val days: Int, val quantity: Int, val unit: String) {
    ONE_DAY(1, 1, "day"),
    TWO_DAY(2, 2, "day"),
    THREE_DAY(3, 3, "day"),
    FOUR_DAY(4, 4, "day"),
    FIVE_DAY(5, 5, "day"),
    SIX_DAY(6, 6, "day"),
    ONE_WEEK(7, 1, "week"),
    TWO_WEEK(14, 2, "week"),
    THREE_WEEK(21, 3, "week"),
    ONE_MONTH(30, 1, "month"),
    TWO_MONTH(60, 2, "month"),
    THREE_MONTH(90, 3, "month"),
    FOUR_MONTH(120, 4, "month"),
    FIVE_MONTH(150, 5, "month"),
    HALF_YEAR(182, 6, "month"),
    ONE_YEAR(365, 1, "year"),
}
